package com.caplin.integration.datasourcex.spring.internal

import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping.Type.MAPPING
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping.Type.RECORD_GENERIC
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping.Type.RECORD_TYPE1
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.net.URLDecoder
import kotlin.reflect.full.isSuperclassOf
import org.springframework.context.EmbeddedValueResolverAware
import org.springframework.core.KotlinDetector
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.convert.ConversionService
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.messaging.Message
import org.springframework.messaging.handler.CompositeMessageCondition
import org.springframework.messaging.handler.DestinationPatternsMessageCondition
import org.springframework.messaging.handler.HandlerMethod
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.ValueConstants
import org.springframework.messaging.handler.annotation.reactive.ContinuationHandlerMethodArgumentResolver
import org.springframework.messaging.handler.annotation.reactive.DestinationVariableMethodArgumentResolver
import org.springframework.messaging.handler.annotation.reactive.HeaderMethodArgumentResolver
import org.springframework.messaging.handler.annotation.reactive.HeadersMethodArgumentResolver
import org.springframework.messaging.handler.annotation.support.AnnotationExceptionHandlerMethodResolver
import org.springframework.messaging.handler.invocation.AbstractExceptionHandlerMethodResolver
import org.springframework.messaging.handler.invocation.reactive.AbstractMethodMessageHandler
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Controller
import org.springframework.util.AntPathMatcher
import org.springframework.util.Assert
import org.springframework.util.CollectionUtils
import org.springframework.util.RouteMatcher
import org.springframework.util.SimpleRouteMatcher
import org.springframework.util.StringValueResolver
import reactor.core.publisher.Mono

internal class DataSourceMessageHandler :
    EmbeddedValueResolverAware, AbstractMethodMessageHandler<CompositeMessageCondition>() {

  var conversionService: ConversionService = DefaultFormattingConversionService()

  private var valueResolver: StringValueResolver? = null

  val routeMatcher = SimpleRouteMatcher(AntPathMatcher())

  init {
    setHandlerPredicate { AnnotatedElementUtils.hasAnnotation(it, Controller::class.java) }
  }

  override fun setEmbeddedValueResolver(resolver: StringValueResolver) {
    valueResolver = resolver
  }

  override fun initArgumentResolvers(): List<HandlerMethodArgumentResolver> = buildList {

    // Annotation-based resolvers
    add(
        object : DestinationVariableMethodArgumentResolver(conversionService) {
          override fun supportsParameter(parameter: MethodParameter): Boolean {
            return super.supportsParameter(parameter) ||
                parameter.hasParameterAnnotation(IngressDestinationVariable::class.java)
          }

          override fun createNamedValueInfo(parameter: MethodParameter): NamedValueInfo {
            val annot =
                parameter.getParameterAnnotation(DestinationVariable::class.java)?.value
                    ?: parameter
                        .getParameterAnnotation(IngressDestinationVariable::class.java)
                        ?.value
            checkNotNull(annot) {
              "No DestinationVariable or IngressDestinationVariable annotation"
            }
            return object : NamedValueInfo(annot, true, ValueConstants.DEFAULT_NONE) {}
          }
        })
    add(HeaderMethodArgumentResolver(conversionService, null))
    add(HeadersMethodArgumentResolver())

    // Type-based...
    if (KotlinDetector.isKotlinPresent()) add(ContinuationHandlerMethodArgumentResolver())

    // Custom resolvers
    addAll(argumentResolverConfigurer.customResolvers)

    // Catch-all
    add(DataSourcePayloadMethodArgumentResolver(reactiveAdapterRegistry, true))
  }

  override fun initReturnValueHandlers(): List<HandlerMethodReturnValueHandler> = buildList {
    add(DataSourcePayloadReturnValueHandler(reactiveAdapterRegistry))
    addAll(returnValueHandlerConfigurer.customHandlers)
  }

  override fun getMappingForMethod(
      method: Method,
      handlerType: Class<*>
  ): CompositeMessageCondition? {
    val methodCondition = getCondition(method)
    if (methodCondition != null) {
      val typeCondition = getCondition(handlerType)
      if (typeCondition != null) return typeCondition.combine(methodCondition)
    }
    return methodCondition
  }

  override fun getDestination(message: Message<*>): RouteMatcher.Route? =
      message.headers[DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER]
          as RouteMatcher.Route?

  override fun getMappingComparator(message: Message<*>): Comparator<CompositeMessageCondition> =
      Comparator { info1, info2 ->
        info1.compareTo(info2, message)
      }

  override fun createExceptionMethodResolverFor(
      beanType: Class<*>
  ): AbstractExceptionHandlerMethodResolver = AnnotationExceptionHandlerMethodResolver(beanType)

  override fun getMatchingMapping(
      mapping: CompositeMessageCondition,
      message: Message<*>,
  ): CompositeMessageCondition? = mapping.getMatchingCondition(message)

  override fun getDirectLookupMappings(mapping: CompositeMessageCondition): Set<String> =
      mapping
          .getCondition(DestinationPatternsMessageCondition::class.java)
          .patterns
          .asSequence()
          .filter { pattern -> !routeMatcher.isPattern(pattern) }
          .toSet()

  override fun extendMapping(
      composite: CompositeMessageCondition,
      handler: HandlerMethod,
  ): CompositeMessageCondition {
    val conditions = composite.messageConditions
    Assert.isTrue(
        conditions.size == 2 &&
            conditions[0] is DataSourceRequestTypeMessageCondition &&
            conditions[1] is DestinationPatternsMessageCondition,
        "Unexpected message condition types",
    )
    if (conditions[0] !== DataSourceRequestTypeMessageCondition.Companion.emptyCondition)
        return composite
    val responseCardinality = getCardinality(handler.returnType)
    var requestCardinality = 0
    var elementType: Class<*>? = null
    for (parameter in handler.methodParameters) {
      val argumentResolver = argumentResolvers.getArgumentResolver(parameter)
      if (argumentResolver is DataSourcePayloadMethodArgumentResolver) {
        requestCardinality = getCardinality(parameter)
        elementType = reactiveAdapterRegistry.resolveElementType(parameter)
      }
    }

    val returnType = reactiveAdapterRegistry.resolveElementType(handler.returnType)!!
    val isSubjectMapping =
        handler.getMethodAnnotation(DataMessageMapping::class.java)?.takeIf {
          it.type == MAPPING
        } != null
    if (isSubjectMapping) {
      check(String::class.isSuperclassOf(returnType.kotlin)) {
        "Methods annotated with @${DataMessageMapping::class.simpleName} with a type of " +
            "$MAPPING must return a String or stream of Strings"
      }
    }

    val recordMessageMappingType =
        handler
            .getMethodAnnotation(DataMessageMapping::class.java)
            ?.takeIf { it.type == RECORD_TYPE1 || it.type == RECORD_GENERIC }
            ?.type
    if (recordMessageMappingType != null) {
      check(Map::class.isSuperclassOf(returnType.kotlin)) {
        "Methods annotated with @${DataMessageMapping::class.simpleName} with a type of " +
            "$recordMessageMappingType must return a Map or stream of Maps"
      }
    }

    val streamType by lazy {
      when {
        isSubjectMapping ->
            DataSourceRequestTypeMessageCondition.RequestType.Stream.ObjectType.MAPPING

        recordMessageMappingType != null ->
            when (recordMessageMappingType) {
              RECORD_GENERIC ->
                  DataSourceRequestTypeMessageCondition.RequestType.Stream.ObjectType.GENERIC

              RECORD_TYPE1 ->
                  DataSourceRequestTypeMessageCondition.RequestType.Stream.ObjectType.TYPE1

              else -> error("Invalid record type: $recordMessageMappingType")
            }

        else -> DataSourceRequestTypeMessageCondition.RequestType.Stream.ObjectType.JSON
      }
    }

    val channelType by lazy {
      check(!isSubjectMapping) {
        "Methods annotated with @${DataMessageMapping::class.simpleName} with a type of " +
            "$MAPPING cannot accept a payload"
      }
      when {
        recordMessageMappingType != null ->
            when (recordMessageMappingType) {
              RECORD_GENERIC ->
                  DataSourceRequestTypeMessageCondition.RequestType.Channel.ObjectType.GENERIC

              RECORD_TYPE1 ->
                  DataSourceRequestTypeMessageCondition.RequestType.Channel.ObjectType.TYPE1

              else -> error("Invalid record type: $recordMessageMappingType")
            }

        else -> DataSourceRequestTypeMessageCondition.RequestType.Channel.ObjectType.JSON
      }
    }

    val condition =
        when (requestCardinality) {
          0 ->
              when {
                responseCardinality == 1 ->
                    DataSourceRequestTypeMessageCondition.Companion.streamStaticCondition(
                        streamType)

                responseCardinality > 1 ->
                    DataSourceRequestTypeMessageCondition.Companion.streamUpdatingCondition(
                        streamType)

                else ->
                    throw IllegalArgumentException(
                        "No receive, no response is not supported - ${handler.method}")
              }

          1 ->
              if (responseCardinality > 0)
                  DataSourceRequestTypeMessageCondition.Companion.channelRequestStreamCondition(
                      elementType,
                      channelType,
                  )
              else
                  DataSourceRequestTypeMessageCondition.Companion.channelFireAndForgetCondition(
                      elementType,
                      channelType,
                  )

          2 ->
              if (responseCardinality > 0)
                  DataSourceRequestTypeMessageCondition.Companion
                      .channelBidirectionalStreamCondition(
                          elementType,
                          channelType,
                      )
              else
                  throw IllegalArgumentException(
                      "Multi receive, single/no response is not supported - return an empty " +
                          "stream if required - ${handler.method}")

          else -> error("Invalid cardinality: $requestCardinality $responseCardinality")
        }

    return CompositeMessageCondition(condition, conditions[1])
  }

  override fun handleMatch(
      mapping: CompositeMessageCondition,
      handlerMethod: HandlerMethod,
      message: Message<*>,
  ): Mono<Void> {
    val patterns = mapping.getCondition(DestinationPatternsMessageCondition::class.java).patterns
    if (!CollectionUtils.isEmpty(patterns)) {
      val pattern = patterns.iterator().next()
      val destination = getDestination(message)
      checkNotNull(destination) { "Missing destination header" }
      val vars = routeMatcher.matchAndExtract(pattern, destination)
      val decodedVars =
          vars?.mapValues {
            URLDecoder.decode(it.value, DataSourceServerBootstrap.Companion.bootstrapCharset)
          }
      if (!CollectionUtils.isEmpty(vars)) {
        val mha = MessageHeaderAccessor.getAccessor(message, MessageHeaderAccessor::class.java)
        Assert.state(mha != null && mha.isMutable, "Mutable MessageHeaderAccessor required")
        mha!!.setHeader(
            DestinationVariableMethodArgumentResolver.DESTINATION_TEMPLATE_VARIABLES_HEADER,
            decodedVars,
        )
      }
    }
    return super.handleMatch(mapping, handlerMethod, message)
  }

  private fun getCondition(element: AnnotatedElement): CompositeMessageCondition? {
    val messageMapping =
        AnnotatedElementUtils.findMergedAnnotation(element, MessageMapping::class.java)
    if (messageMapping == null || messageMapping.value.isEmpty()) {
      return null
    }
    val patterns: Array<String> = processDestinations(messageMapping.value)

    return CompositeMessageCondition(
        DataSourceRequestTypeMessageCondition.Companion.emptyCondition,
        DestinationPatternsMessageCondition(patterns, routeMatcher),
    )
  }

  private fun processDestinations(destinations: Array<String>): Array<String> {
    var resolvedDestinations = destinations
    val valueResolver = this.valueResolver
    if (valueResolver != null) {
      resolvedDestinations =
          resolvedDestinations
              .map {
                checkNotNull(valueResolver.resolveStringValue(it)) { "Destination value was null" }
              }
              .toTypedArray()
    }
    return resolvedDestinations
  }

  private fun getCardinality(parameter: MethodParameter): Int {
    val clazz = parameter.parameterType
    val adapter = reactiveAdapterRegistry.getAdapter(clazz)
    return when {
      adapter == null -> if (clazz == Void.TYPE) 0 else 1
      parameter.nested().nestedParameterType == Void::class.java -> 0
      else -> if (adapter.isMultiValue) 2 else 1
    }
  }
}
