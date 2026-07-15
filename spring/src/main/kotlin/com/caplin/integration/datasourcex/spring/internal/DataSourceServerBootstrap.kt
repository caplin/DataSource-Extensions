package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.Service
import com.caplin.integration.datasourcex.reactive.api.ActiveContainerConfig.Companion.ITEMS_SUFFIX_DEFAULT
import com.caplin.integration.datasourcex.reactive.api.DataSourceSettings
import com.caplin.integration.datasourcex.reactive.api.RecordType
import com.caplin.integration.datasourcex.reactive.kotlin.bind
import com.caplin.integration.datasourcex.spring.annotations.DataService
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import com.caplin.integration.datasourcex.spring.internal.DataSourceRequestTypeMessageCondition.RequestType
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import com.caplin.integration.datasourcex.util.AntPatternNamespace.Companion.addIncludeNamespace
import com.caplin.integration.datasourcex.util.LifecycleDataSource
import com.caplin.integration.datasourcex.util.Subject.Companion.path
import com.caplin.integration.datasourcex.util.getLogger
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.context.SmartLifecycle
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.handler.DestinationPatternsMessageCondition
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.util.RouteMatcher

/**
 * Bootstraps Spring Messaging endpoints annotated with
 * [org.springframework.messaging.handler.annotation.MessageMapping] with DataSource.
 */
internal class DataSourceServerBootstrap(
    private val dataSource: LifecycleDataSource,
    private val dataSourceMessageHandler: DataSourceMessageHandler?,
    private val dataSourceInfo: DataSourceInfo,
    private val decodeUsernameObjectMappings: Boolean = false,
) : ApplicationEventPublisherAware, SmartLifecycle {

  companion object {
    private val logger = getLogger<DataSourceServerBootstrap>()
    private val emptyPayload = emptyFlow<Nothing>().asPublisher()

    internal val bootstrapCharset = AntPatternNamespace.CHARSET
    private val fireAndForgetOkResponse = mapOf("Result" to "ok")
  }

  private var running = false

  private lateinit var eventPublisher: ApplicationEventPublisher

  override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
    eventPublisher = applicationEventPublisher
  }

  override fun start() {
    checkNotNull(dataSource.extraConfiguration.jsonHandler) { "No JsonHandler found on DataSource" }

    DataSourceSettings.decodeUsernameObjectMappings = decodeUsernameObjectMappings

    val services = mutableMapOf<String, Service>()
    dataSourceMessageHandler?.run {
      dataSource.bind {
        handlerMethods.forEach { (conditions, method) ->
          val dataService =
              AnnotatedElementUtils.findMergedAnnotation(method.beanType, DataService::class.java)
          val name =
              dataService?.value?.takeIf { it.isNotBlank() }
                  ?: "${dataSourceInfo.name}-${method.beanType.canonicalName}"

          val service =
              services.getOrPut(name) {
                val remoteLabelPattern =
                    (dataService?.remoteLabelPattern?.takeIf { it.isNotEmpty() }
                        ?: dataSourceInfo.remoteLabelPattern)
                logger.info {
                  "Service $name: created with remote label pattern $remoteLabelPattern"
                }
                Service.named(name)
                    .setRemoteLabelPattern(remoteLabelPattern)
                    .setDiscardTimeout(
                        dataService?.let {
                          Duration.of(it.discardTimeout, it.timeUnit.toChronoUnit())
                        } ?: Duration.ofMinutes(1)
                    )
              }

          val messageCondition =
              conditions.getCondition(DataSourceRequestTypeMessageCondition::class.java)
          val destinationMessageCondition =
              conditions.getCondition(DestinationPatternsMessageCondition::class.java)

          val dataSourceRequestType = messageCondition.requestTypes.single()
          logger.info {
            "Registering ${destinationMessageCondition.patterns} as $dataSourceRequestType"
          }

          val pattern = destinationMessageCondition.patterns.single()

          val mappings =
              method.methodParameters
                  .mapNotNull { parameter ->
                    parameter.initParameterNameDiscovery(DefaultParameterNameDiscoverer())
                    parameter.getParameterAnnotation(IngressDestinationVariable::class.java)?.let {
                        destinationVariable ->
                      val variableName =
                          checkNotNull(
                              destinationVariable.value.takeIf { it.isNotEmpty() }
                                  ?: parameter.parameterName
                          ) {
                            "Unable to resolve name for $parameter"
                          }
                      variableName to destinationVariable.token
                    }
                  }
                  .toMap()

          val isContainer =
              dataSourceRequestType is RequestType.Stream &&
                  when (dataSourceRequestType.type) {
                    RequestType.Stream.ObjectType.CONTAINER_JSON,
                    RequestType.Stream.ObjectType.CONTAINER_TYPE1,
                    RequestType.Stream.ObjectType.CONTAINER_GENERIC -> true
                    else -> false
                  }

          val namespaces = buildList {
            add(AntPatternNamespace(pattern))
            if (isContainer) {
              add(AntPatternNamespace("$pattern${ITEMS_SUFFIX_DEFAULT}/*"))
            }
          }

          namespaces.forEach { ns ->
            service.addIncludeNamespace(ns)
            logger.info { "Service $name: Adding include namespace $ns" }

            if (mappings.isNotEmpty()) {
              val antPattern = ns.pattern
              if (antPattern.contains("""/\*\*/""".toRegex())) {
                logger.warn {
                  "Multi-directory wildcard with suffix (e.g. `prefix/**/suffix`) used in " +
                      "pattern $antPattern. Object mapping can only by automatically configured " +
                      "for the first directory level (e.g. `prefix/*/suffix`)."
                }
              }

              val (fromPattern, toPattern) = ns.getObjectMap(mappings)
              service.addObjectMap(fromPattern, toPattern)
              logger.info { "Service $name: Adding object mapping $fromPattern -> $toPattern" }
            }
          }

          when (dataSourceRequestType) {
            is RequestType.Channel -> {
              fun <I : Any> createFlow(path: String, receiveFlow: Flow<Any>): Flow<I> =
                  flow {
                        val sendFlow = AtomicReference<Flow<I>>()
                        val route = routeMatcher.parseRoute(path)
                        handleMessage(
                                MessageBuilder.createMessage(
                                    receiveFlow,
                                    createHeaders(route, dataSourceRequestType, sendFlow),
                                )
                            )
                            .awaitSingleOrNull()

                        @Suppress("UNCHECKED_CAST")
                        if (dataSourceRequestType.fireAndForget) emit(fireAndForgetOkResponse as I)
                        else emitAll(sendFlow.get())
                      }
                      .onStart {
                        logger.info {
                          "$path matching ${destinationMessageCondition.patterns} started"
                        }
                      }
                      .onCompletion {
                        logger.info {
                          "$path matching ${destinationMessageCondition.patterns} completed"
                        }
                      }

              when (val type = dataSourceRequestType.type) {
                RequestType.Channel.ObjectType.JSON -> {
                  channel {
                    json {
                      pattern(
                          pattern,
                          dataSourceRequestType.payloadType!!,
                          {
                            channelType = dataSourceRequestType.channelType
                            objectMappings = mappings
                          },
                      ) {
                        createFlow(path, receive)
                      }
                    }
                  }
                }

                RequestType.Channel.ObjectType.TYPE1,
                RequestType.Channel.ObjectType.GENERIC -> {
                  channel {
                    record {
                      pattern(
                          pattern,
                          {
                            channelType = dataSourceRequestType.channelType
                            objectMappings = mappings
                            recordType =
                                when (type) {
                                  RequestType.Channel.ObjectType.TYPE1 -> RecordType.TYPE1

                                  RequestType.Channel.ObjectType.GENERIC -> RecordType.GENERIC

                                  else -> error("Unreachable")
                                }
                          },
                      ) {
                        createFlow(path, receive)
                      }
                    }
                  }
                }
              }
            }

            is RequestType.Stream -> {
              fun <I : Any> createFlow(path: String) =
                  flow {
                        val sendFlow = AtomicReference<Flow<I>>()
                        val route = routeMatcher.parseRoute(path)
                        handleMessage(
                                MessageBuilder.createMessage(
                                    emptyPayload,
                                    createHeaders(route, dataSourceRequestType, sendFlow),
                                )
                            )
                            .awaitSingleOrNull()

                        emitAll(sendFlow.get())
                        if (dataSourceRequestType is RequestType.Stream.Static) awaitCancellation()
                      }
                      .onStart {
                        logger.info {
                          "$path matching ${destinationMessageCondition.patterns} started"
                        }
                      }
                      .onCompletion {
                        logger.info {
                          "$path matching ${destinationMessageCondition.patterns} completed"
                        }
                      }

              when (val type = dataSourceRequestType.type) {
                RequestType.Stream.ObjectType.MAPPING ->
                    active {
                      mapping {
                        pattern(pattern, { objectMappings = mappings }) { createFlow(path) }
                      }
                    }

                RequestType.Stream.ObjectType.JSON ->
                    active {
                      json { pattern(pattern, { objectMappings = mappings }) { createFlow(path) } }
                    }

                RequestType.Stream.ObjectType.CONTAINER_JSON ->
                    activeContainer {
                      json { pattern(pattern, { objectMappings = mappings }) { createFlow(path) } }
                    }

                RequestType.Stream.ObjectType.TYPE1,
                RequestType.Stream.ObjectType.GENERIC ->
                    active {
                      record {
                        pattern(
                            pattern,
                            {
                              objectMappings = mappings
                              recordType =
                                  when (type) {
                                    RequestType.Stream.ObjectType.TYPE1 -> RecordType.TYPE1

                                    RequestType.Stream.ObjectType.GENERIC -> RecordType.GENERIC

                                    else -> error("Unreachable")
                                  }
                            },
                        ) {
                          createFlow(path)
                        }
                      }
                    }

                RequestType.Stream.ObjectType.CONTAINER_TYPE1,
                RequestType.Stream.ObjectType.CONTAINER_GENERIC ->
                    activeContainer {
                      record {
                        pattern(
                            pattern,
                            {
                              objectMappings = mappings
                              rowRecordType =
                                  when (type) {
                                    RequestType.Stream.ObjectType.CONTAINER_TYPE1 ->
                                        RecordType.TYPE1

                                    RequestType.Stream.ObjectType.CONTAINER_GENERIC ->
                                        RecordType.GENERIC

                                    else -> error("Unreachable")
                                  }
                            },
                        ) {
                          createFlow(path)
                        }
                      }
                    }
              }
            }
          }
        }
      }
    }

    services.values.forEach(dataSource::createService)

    dataSource.start()
    running = true
    eventPublisher.publishEvent(DataSourceServerInitializedEvent(dataSource))
  }

  override fun stop() {
    dataSource.stop()
    running = false
  }

  override fun isRunning(): Boolean = running

  private fun createHeaders(
      route: RouteMatcher.Route,
      requestType: RequestType,
      sendFlow: AtomicReference<out Flow<Any>>,
  ): MessageHeaders =
      MessageHeaderAccessor()
          .apply {
            setLeaveMutable(true)
            setHeader(
                DataSourceRequestTypeMessageCondition.REQUEST_TYPE_HEADER,
                requestType,
            )
            setHeader(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER, route)
            setHeader(DataSourcePayloadReturnValueHandler.RESPONSE_HEADER, sendFlow)
          }
          .messageHeaders
}
