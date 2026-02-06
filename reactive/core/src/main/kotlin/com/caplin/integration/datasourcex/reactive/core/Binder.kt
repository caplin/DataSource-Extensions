@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.ConnectionListener
import com.caplin.datasource.DataSource
import com.caplin.datasource.Peer
import com.caplin.datasource.PeerStatus
import com.caplin.datasource.Service
import com.caplin.datasource.Service.Type.CONTRIB
import com.caplin.datasource.SubjectError
import com.caplin.datasource.channel.ChannelListener
import com.caplin.datasource.channel.JsonChannel
import com.caplin.datasource.channel.JsonChannelListener
import com.caplin.datasource.internal.configuration.AttributeConfiguration.DATASRC_LOCAL_LABEL
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.Message
import com.caplin.datasource.messaging.json.JsonChannelMessage
import com.caplin.datasource.messaging.mapping.MappingMessage
import com.caplin.datasource.messaging.record.RecordMessage
import com.caplin.datasource.publisher.CachingDataProvider
import com.caplin.datasource.publisher.CachingPublisher
import com.caplin.datasource.publisher.DataProvider
import com.caplin.datasource.publisher.DiscardEvent
import com.caplin.datasource.publisher.Publisher
import com.caplin.datasource.publisher.RequestEvent
import com.caplin.integration.datasourcex.reactive.api.ActiveConfig
import com.caplin.integration.datasourcex.reactive.api.ActiveContainerConfig
import com.caplin.integration.datasourcex.reactive.api.BroadcastConfig
import com.caplin.integration.datasourcex.reactive.api.BroadcastEvent
import com.caplin.integration.datasourcex.reactive.api.ChannelConfig
import com.caplin.integration.datasourcex.reactive.api.ChannelType
import com.caplin.integration.datasourcex.reactive.api.ConfigBlock
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent
import com.caplin.integration.datasourcex.reactive.api.InsertAt
import com.caplin.integration.datasourcex.reactive.api.PathSupplier
import com.caplin.integration.datasourcex.reactive.api.PathVariablesChannelSupplier
import com.caplin.integration.datasourcex.reactive.api.RecordType
import com.caplin.integration.datasourcex.reactive.api.ServiceConfig
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import com.caplin.integration.datasourcex.util.AntPatternNamespace.Companion.addIncludeNamespace
import com.caplin.integration.datasourcex.util.getLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect

class Binder
private constructor(val dataSource: ScopedDataSource, private val serviceInfo: ServiceInfo?) {

  constructor(dataSource: ScopedDataSource) : this(dataSource, null)

  private companion object {
    private val logger = getLogger<Binder>()
  }

  private data class ServiceInfo(
      val service: Service,
      val serviceConfig: ServiceConfig,
  )

  private data class MappingInfo(val state: StateFlow<String?>, val job: Job)

  private data class ChannelInfo<R : Any>(val job: Job, val fromClientChannel: Channel<R>) {
    fun cancel() {
      job.cancel()
      fromClientChannel.close()
    }
  }

  fun withServiceConfig(serviceConfig: ServiceConfig, block: (Binder) -> Unit) {
    check(this.serviceInfo == null) {
      "Nested services are not supported, `${this.serviceInfo?.serviceConfig?.name.orEmpty()}` " +
          "is currently being configured."
    }
    val serviceInfo = ServiceInfo(Service.named(serviceConfig.name), serviceConfig)
    block(Binder(dataSource, serviceInfo))
    serviceInfo.finalise(dataSource)
  }

  fun bindActiveMapping(
      configure: ConfigBlock<ActiveConfig.Mapping>,
      namespace: AntPatternNamespace,
      supplier: PathSupplier<Flow<String>>,
  ) {
    val config = with(configure) { ActiveConfig.Mapping().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, config.objectMappings)

    dataSource.createActivePublisher(
        namespace,
        object : DataProvider {

          private val subscriptions = ConcurrentHashMap<String, MappingInfo>()

          private lateinit var publisher: Publisher

          override fun setPublisher(publisher: Publisher) {
            this.publisher = publisher
          }

          override fun onRequest(request: RequestEvent) {
            fun publishMapping(value: String) {
              publisher.publishMappingMessage(
                  publisher.messageFactory.createMappingMessage(request.subject, value),
              )
            }

            subscriptions
                .computeIfAbsent(request.subject) { subject ->
                  val state =
                      supplier(subject)
                          .catch {
                            subscriptions.remove(subject)
                            publisher.publishNotFound(subject)
                          }
                          .stateIn(
                              dataSource,
                              started = SharingStarted.Companion.WhileSubscribed(),
                              null,
                          )
                  val job = state.filterNotNull().onEach(::publishMapping).launchIn(dataSource)

                  MappingInfo(state, job)
                }
                .state
                .value
                ?.let(::publishMapping)
          }

          override fun onDiscard(discard: DiscardEvent) {
            subscriptions.remove(discard.subject)?.job?.cancel()
          }
        },
    )
  }

  fun bindActiveJson(
      configure: ConfigBlock<ActiveConfig.Json>,
      namespace: AntPatternNamespace,
      supplier: PathSupplier<Flow<Any>>,
  ) {
    val config = with(configure) { ActiveConfig.Json().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, config.objectMappings)

    with(JsonContext()) {
      bindActiveSubjects(namespace, { supplier(it) }) { subject, value ->
        createMessage(subject, value)
      }
    }
  }

  fun bindActiveRecord(
      configure: ConfigBlock<ActiveConfig.Record>,
      namespace: AntPatternNamespace,
      supplier: PathSupplier<Flow<Map<String, String>>>,
  ) {
    val config = with(configure) { ActiveConfig.Record().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, config.objectMappings)

    with(RecordContext(config.images, config.recordType)) {
      bindActiveSubjects(namespace, { supplier(it) }) { subject, value ->
        createMessage(subject, value)
      }
    }
  }

  fun bindActiveContainerJson(
      configure: ConfigBlock<ActiveContainerConfig.Json>,
      namespace: AntPatternNamespace,
      supplier: (path: String) -> Flow<ContainerEvent<Any>>,
  ) {
    val config = with(configure) { ActiveContainerConfig.Json().apply { invoke() } }
    with(JsonContext()) {
      bindContainers(
          namespace,
          config,
          supplier,
      ) { subject, value ->
        createMessage(subject, value)
      }
    }
  }

  fun bindActiveContainerRecord(
      configure: ConfigBlock<ActiveContainerConfig.Record>,
      namespace: AntPatternNamespace,
      supplier: (path: String) -> Flow<ContainerEvent<Map<String, String>>>,
  ) {
    val config = with(configure) { ActiveContainerConfig.Record().apply { invoke() } }
    with(RecordContext(config.rowImages, config.rowRecordType)) {
      bindContainers(
          namespace,
          config,
          supplier,
      ) { subject, value ->
        createMessage(subject, value)
      }
    }
  }

  fun bindChannelRecord(
      configure: ConfigBlock<ChannelConfig.Record>,
      namespace: AntPatternNamespace,
      supplier: PathVariablesChannelSupplier<Flow<Map<String, String>>, Flow<Map<String, String>>>,
  ) {
    val config = with(configure) { ChannelConfig.Record().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, config.objectMappings)

    val type = config.channelType

    val bindFunction =
        if (config.recordType == RecordType.GENERIC) dataSource::addGenericChannelListener
        else dataSource::addChannelListener

    with(RecordContext(config.images, config.recordType)) {
      bindFunction(
          namespace,
          object : ChannelListener {
            private val openChannels = mutableSetOf<String>()
            private val channelInfos = ConcurrentHashMap<String, ChannelInfo<Map<String, String>>>()

            override fun onChannelOpen(channel: com.caplin.datasource.channel.Channel): Boolean =
                openChannels.add(channel.subject).also { opened ->
                  if (opened) logger.info { "Opening channel for ${channel.subject}" }
                  if (type == ChannelType.BIDIRECTIONAL_STREAM) getOrInitChannel(channel)
                }

            override fun onChannelClose(channel: com.caplin.datasource.channel.Channel) {
              logger.info { "Closing channel for ${channel.subject}" }
              openChannels.remove(channel.subject)
              channelInfos.remove(channel.subject)?.cancel()
            }

            override fun onMessageReceived(
                channel: com.caplin.datasource.channel.Channel,
                message: RecordMessage,
            ) {
              val channelInfo = getOrInitChannel(channel)
              channelInfo.fromClientChannel.trySendBlocking(
                  message.fields.associateBy({ it.name }) { it.value },
              )
            }

            private fun getOrInitChannel(
                channel: com.caplin.datasource.channel.Channel
            ): ChannelInfo<Map<String, String>> {
              var channelInfo = channelInfos[channel.subject]

              if (channelInfo != null && type == ChannelType.UNIDIRECTIONAL_STREAM) {
                logger.warn { "Cancelling previous stream request on ${channel.subject}" }
                channelInfo.cancel()
                channelInfo = null
              }

              if (channelInfo == null) {
                val fromClientChannel =
                    Channel<Map<String, String>>(
                        when (type) {
                          ChannelType.BIDIRECTIONAL_STREAM -> Channel.Factory.BUFFERED
                          ChannelType.UNIDIRECTIONAL_STREAM -> 1
                        },
                    )

                val sendToClientJob =
                    supplier(
                            channel.subject,
                            namespace.extractPathVariables(channel.subject),
                            fromClientChannel.consumeAsFlow(),
                        )
                        .onEach { value -> channel.sendMessage(channel.createMessage(value)) }
                        .onCompletion { throwable ->
                          // Server flow completed, i.e. Server closed channel
                          fromClientChannel.close()
                          if (throwable == null)
                              channel.sendSubjectError(channel.subject, SubjectError.NotFound)
                        }
                        .catch { throwable ->
                          logger.warn(throwable) { "Unhandled exception in ${channel.subject}" }
                          channel.sendSubjectError(channel.subject, SubjectError.NotFound)
                        }
                        .launchIn(dataSource)

                channelInfo = ChannelInfo(sendToClientJob, fromClientChannel)
                channelInfos[channel.subject] = channelInfo
              }
              return channelInfo
            }
          },
      )
    }
  }

  fun <R : Any> bindChannelJson(
      configure: ConfigBlock<ChannelConfig.Json>,
      namespace: AntPatternNamespace,
      receiveType: Class<R>,
      supplier: PathVariablesChannelSupplier<Flow<R>, Flow<Any>>,
  ) {
    val config = with(configure) { ChannelConfig.Json().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, config.objectMappings)

    val type = config.channelType
    with(JsonContext()) {
      dataSource.addJsonChannelListener(
          namespace,
          object : JsonChannelListener {
            private val openChannels = mutableSetOf<String>()
            private val channelInfos = ConcurrentHashMap<String, ChannelInfo<R>>()

            override fun onChannelOpen(channel: JsonChannel): Boolean =
                openChannels.add(channel.subject).also { opened ->
                  if (opened) logger.info { "Opening channel for ${channel.subject}" }
                  if (type == ChannelType.BIDIRECTIONAL_STREAM) getOrInitChannel(channel)
                }

            override fun onChannelClose(channel: JsonChannel) {
              logger.info { "Closing channel for ${channel.subject}" }
              openChannels.remove(channel.subject)
              channelInfos.remove(channel.subject)?.cancel()
            }

            override fun onMessageReceived(channel: JsonChannel, message: JsonChannelMessage) {
              val channelInfo = getOrInitChannel(channel)
              channelInfo.fromClientChannel.trySendBlocking(message.getJsonAsType(receiveType))
            }

            private fun getOrInitChannel(channel: JsonChannel): ChannelInfo<R> {
              var channelInfo = channelInfos[channel.subject]

              if (channelInfo != null && type == ChannelType.UNIDIRECTIONAL_STREAM) {
                logger.warn { "Cancelling previous stream request on ${channel.subject}" }
                channelInfo.cancel()
                channelInfo = null
              }

              if (channelInfo == null) {
                val fromClientChannel =
                    Channel<R>(
                        when (type) {
                          ChannelType.BIDIRECTIONAL_STREAM -> Channel.Factory.BUFFERED
                          ChannelType.UNIDIRECTIONAL_STREAM -> 1
                        },
                    )

                val sendToClientJob =
                    supplier(
                            channel.subject,
                            namespace.extractPathVariables(channel.subject),
                            fromClientChannel.consumeAsFlow(),
                        )
                        .onEach { value -> channel.send(value) }
                        .onCompletion { throwable ->
                          // Server flow completed, i.e. Server closed channel
                          fromClientChannel.close()
                          if (throwable == null)
                              channel.sendSubjectError(channel.subject, SubjectError.NotFound)
                        }
                        .catch { throwable ->
                          logger.warn(throwable) { "Unhandled exception in ${channel.subject}" }
                          channel.sendSubjectError(channel.subject, SubjectError.NotFound)
                        }
                        .launchIn(dataSource)

                channelInfo = ChannelInfo(sendToClientJob, fromClientChannel)
                channelInfos[channel.subject] = channelInfo
              }
              return channelInfo
            }
          },
      )
    }
  }

  fun bindBroadcastMapping(
      configure: ConfigBlock<BroadcastConfig.Mapping>,
      namespace: AntPatternNamespace,
      flow: Flow<BroadcastEvent<String>>,
  ) {
    val config = with(configure) { BroadcastConfig.Mapping().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, emptyMap())

    dataSource.launch {
      val upstreamChannel = flow.produceIn(this)

      val newPeerChannel: ReceiveChannel<Peer>?
      val cachedEvents: MutableMap<String, MappingMessage>?
      if (config.cache) {
        newPeerChannel = produce {
          val connectionListener = ConnectionListener { peerStatusEvent ->
            if (peerStatusEvent.peerStatus == PeerStatus.UP) trySendBlocking(peerStatusEvent.peer)
          }
          dataSource.addConnectionListener(connectionListener)

          awaitClose { dataSource.removeConnectionListener(connectionListener) }
        }
        cachedEvents = mutableMapOf()
      } else {
        newPeerChannel = null
        cachedEvents = null
      }

      val publisher = dataSource.createBroadcastPublisher(namespace)

      whileSelect {
        upstreamChannel.onReceiveCatching { result ->
          result
              .onSuccess {
                val message = publisher.messageFactory.createMappingMessage(it.subject, it.value)
                cachedEvents?.put(it.subject, message)

                publisher.publishMappingMessage(message)
              }
              .isSuccess
        }
        newPeerChannel?.onReceive { peer ->
          cachedEvents!!.values.forEach { message -> publisher.publishToPeer(peer, message) }
          true
        }
      }

      newPeerChannel?.cancel()
    }
  }

  fun bindBroadcastRecord(
      configure: ConfigBlock<BroadcastConfig.Record>,
      namespace: AntPatternNamespace,
      flow: Flow<BroadcastEvent<Map<String, String>>>,
  ) {
    val config = with(configure) { BroadcastConfig.Record().apply { invoke() } }
    serviceInfo?.registerNamespace(namespace, emptyMap())

    with(RecordContext(true, config.recordType)) {
      dataSource.launch {
        val upstreamChannel = flow.produceIn(this)

        val newPeerChannel: ReceiveChannel<Peer>?
        val cachedEvents: MutableMap<String, Message>?
        if (config.cache) {
          newPeerChannel = produce {
            val connectionListener = ConnectionListener { peerStatusEvent ->
              if (peerStatusEvent.peerStatus == PeerStatus.UP) trySendBlocking(peerStatusEvent.peer)
            }
            dataSource.addConnectionListener(connectionListener)

            awaitClose { dataSource.removeConnectionListener(connectionListener) }
          }
          cachedEvents = mutableMapOf()
        } else {
          newPeerChannel = null
          cachedEvents = null
        }

        val publisher = dataSource.createBroadcastPublisher(namespace)

        whileSelect {
          upstreamChannel.onReceiveCatching { result ->
            result
                .onSuccess {
                  val message = publisher.messageFactory.createMessage(it.subject, it.value)
                  cachedEvents?.put(it.subject, message)

                  // Implementation of this appears to set the image flag and publish to all peers,
                  // so we don't
                  // need to also call publishToSubscribedPeers, however it means that we should
                  // always publish
                  // with the image flag set.
                  publisher.publishInitialMessage(message)
                }
                .isSuccess
          }
          newPeerChannel?.onReceive { peer ->
            cachedEvents!!.values.forEach { message -> publisher.publishToPeer(peer, message) }
            true
          }
        }

        newPeerChannel?.cancel()
      }
    }
  }

  private fun <T : Any> bindContainers(
      containerSubjectPattern: AntPatternNamespace,
      config: ActiveContainerConfig,
      createFlow: (containerSubject: String) -> Flow<ContainerEvent<T>>,
      createMessage: CachedMessageFactory.(subject: String, value: T) -> Message,
  ) {
    val rowPattern =
        AntPatternNamespace("${containerSubjectPattern.pattern}${config.rowPathSuffix}/{itemId}")
    logger.info { "Using item pattern $rowPattern for container pattern $containerSubjectPattern" }

    serviceInfo?.registerNamespace(containerSubjectPattern, config.objectMappings)
    serviceInfo?.registerNamespace(rowPattern, emptyMap())

    val containers = ConcurrentHashMap<String, Container<T>>()

    dataSource.createCachingPublisher(
        rowPattern,
        object : CachingDataProvider {
          private val subscriptions = ConcurrentHashMap<String, Job>()

          private lateinit var publisher: CachingPublisher

          override fun setPublisher(cachingPublisher: CachingPublisher) {
            publisher = cachingPublisher
          }

          override fun onRequest(subject: String): Unit =
              with(publisher) {
                val containerSubject =
                    subject
                        .split("/")
                        .dropLast(1)
                        .joinToString("/")
                        .removeSuffix(config.rowPathSuffix)

                val container = containers[containerSubject]
                val job =
                    if (container == null) {
                      logger.warn { "Container $containerSubject does not exist" }
                      publishNotFound(subject)
                      null
                    } else {

                      // We don't want to spam Liberator if the upstream completes or errors -
                      // we just send "NotFound" for
                      // the container and don't send anything for the records.
                      // When the container removes a record, we also do not send anything on
                      // the record, as we assume
                      // that the removeElement is enough.
                      container
                          .getRowFlow(subject)
                          .map { value -> cachedMessageFactory.createMessage(subject, value) }
                          .onCompletion { throwable ->
                            if (throwable == null) {
                              logger.warn { "Row $subject does not exist" }
                              publishNotFound(subject)
                            }
                          }
                          .onEach { message -> publish(message) }
                          .launchIn(container.scope)
                    }

                job?.apply { invokeOnCompletion { subscriptions.remove(subject) } }
                    ?.also { subscriptions[subject] = it }
              }

          override fun onDiscard(subject: String) {
            subscriptions.remove(subject)?.cancel()
          }
        },
    )

    dataSource.createCachingPublisher(
        containerSubjectPattern,
        object : CachingDataProvider {

          private val subscriptions = ConcurrentHashMap<String, Job>()

          private lateinit var publisher: CachingPublisher

          override fun setPublisher(cachingPublisher: CachingPublisher) {
            publisher = cachingPublisher
          }

          override fun onRequest(subject: String): Unit =
              with(publisher) {
                dataSource
                    .launch {
                      val container =
                          Container(
                              containerSubject = subject,
                              config = config,
                              supplier = createFlow,
                              scope = this,
                          )
                      containers[subject] = container
                      try {
                        container.containerEventsFlow
                            .onCompletion { throwable ->
                              if (throwable == null) publishNotFound(subject)
                            }
                            .catch { throwable ->
                              publishNotFound(subject)
                              logger.warn(throwable) { "Unhandled exception in $subject" }
                            }
                            .collect { containerEvents ->
                              val containerMessage =
                                  cachedMessageFactory.createContainerMessage(subject).apply {
                                    isDoNotAuthenticate = true
                                    containerEvents.forEach { containerEvent ->
                                      when (containerEvent) {
                                        is InternalContainerEvent.Inserted -> {
                                          logger.debug {
                                            "Inserted row ${containerEvent.subject} in $subject"
                                          }
                                          when (config.insertAt) {
                                            InsertAt.HEAD ->
                                                insertElement(containerEvent.subject, 0)

                                            InsertAt.TAIL -> addElement(containerEvent.subject)
                                          }
                                        }

                                        is InternalContainerEvent.Removed -> {
                                          logger.debug {
                                            "Removed row ${containerEvent.subject} in $subject"
                                          }
                                          removeElement(containerEvent.subject)
                                        }
                                      }
                                    }
                                  }

                              publish(containerMessage)
                            }
                      } finally {
                        containers.remove(subject)
                      }
                      cancel()
                    }
                    .apply { invokeOnCompletion { subscriptions.remove(subject) } }
                    .also { job -> subscriptions[subject] = job }
              }

          override fun onDiscard(subject: String) {
            subscriptions.remove(subject)?.cancel()
          }
        },
    )
  }

  private fun <T : Any> bindActiveSubjects(
      namespace: AntPatternNamespace,
      createFlow: (subject: String) -> Flow<T>,
      createMessage: CachedMessageFactory.(subject: String, value: T) -> Message,
  ) {
    dataSource.createCachingPublisher(
        namespace,
        object : CachingDataProvider {

          private val subscriptions = ConcurrentHashMap<String, Job>()

          private lateinit var publisher: CachingPublisher

          override fun setPublisher(cachingPublisher: CachingPublisher) {
            publisher = cachingPublisher
          }

          override fun onRequest(subject: String) {
            check(!subscriptions.containsKey(subject)) {
              "Multiple subscriptions to the same subject ($subject)"
            }
            subscriptions[subject] =
                flow { emitAll(createFlow(subject)) }
                    .map { t -> publisher.cachedMessageFactory.createMessage(subject, t) }
                    .onEach { message -> publisher.publish(message) }
                    .onCompletion { throwable ->
                      if (throwable == null) publisher.publishNotFound(subject)
                    }
                    .catch { throwable ->
                      logger.warn(throwable) { "Unhandled exception in $subject" }
                      publisher.publishNotFound(subject)
                    }
                    .launchIn(dataSource)
                    .apply { invokeOnCompletion { subscriptions.remove(subject) } }
          }

          override fun onDiscard(subject: String) {
            subscriptions.remove(subject)?.cancel()
          }
        },
    )
  }

  private fun CachingPublisher.publishNotFound(subject: String) {
    publishSubjectErrorEvent(
        cachedMessageFactory.createSubjectErrorEvent(subject, SubjectError.NotFound),
    )
  }

  private fun Publisher.publishNotFound(subject: String) {
    publishSubjectErrorEvent(messageFactory.createSubjectErrorEvent(subject, SubjectError.NotFound))
  }

  private fun ServiceInfo.registerNamespace(
      antPatternNamespace: AntPatternNamespace,
      mappings: Map<String, String>?,
  ) {
    mappings?.takeIf(Map<String, String>::isNotEmpty)?.let {
      val (fromPattern, toPattern) = antPatternNamespace.getObjectMap(mappings)
      service.addObjectMap(fromPattern, toPattern)
    }
    service.addIncludeNamespace(antPatternNamespace)
  }

  private fun ServiceInfo.finalise(dataSource: DataSource) {
    (serviceConfig.remoteLabelPattern
            ?: dataSource.configuration.getStringValue(DATASRC_LOCAL_LABEL))
        ?.let(service::setRemoteLabelPattern)

    serviceConfig.discardTimeout?.let(service::setDiscardTimeout)
    serviceConfig.throttleTime?.let(service::setThrottleTime)
    serviceConfig.requiredState?.let(service::setRequiredState)
    serviceConfig.ifLabelPatterns?.forEach(service::addIfLabelPattern)
    service.setType(CONTRIB)

    dataSource.createService(service)
  }
}
