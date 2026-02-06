package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.messaging.json.JsonChannelMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.asFlux
import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.ResolvableType
import org.springframework.messaging.Message
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DataSourcePayloadMethodArgumentResolver(
    private val registry: ReactiveAdapterRegistry,
    private val useDefaultResolution: Boolean,
) : HandlerMethodArgumentResolver {

  override fun supportsParameter(parameter: MethodParameter): Boolean =
      parameter.hasParameterAnnotation(Payload::class.java) || useDefaultResolution

  override fun resolveArgument(parameter: MethodParameter, message: Message<*>): Mono<Any> {
    val payloadAnnotation = parameter.getParameterAnnotation(Payload::class.java)
    check(!(payloadAnnotation != null && payloadAnnotation.expression.isNotEmpty())) {
      "@Payload SpEL expressions not supported by this resolver"
    }

    @Suppress("UNCHECKED_CAST") val payload = message.payload as Flow<JsonChannelMessage>
    val content = payload.asFlux()

    val adapter = registry.resolveAdapter(ResolvableType.forMethodParameter(parameter))
    val isContentRequired = payloadAnnotation?.required == true || adapter?.supportsEmpty() == false

    return if (adapter?.isMultiValue == true) {
      var flux = content
      if (isContentRequired)
          flux = flux.switchIfEmpty(Flux.error { handleMissingBody(parameter, message) })
      Mono.just(adapter.fromPublisher(flux))
    } else {
      var mono = content.next()
      if (isContentRequired)
          mono = mono.switchIfEmpty(Mono.error { handleMissingBody(parameter, message) })
      if (adapter != null) Mono.just(adapter.fromPublisher(mono)) else Mono.from(mono)
    }
  }

  private fun handleMissingBody(
      param: MethodParameter,
      message: Message<*>,
  ): MethodArgumentResolutionException =
      MethodArgumentResolutionException(
          message,
          param,
          "Payload content is missing: " + param.executable.toGenericString(),
      )
}

internal fun ReactiveAdapterRegistry.resolveAdapter(
    resolvableType: ResolvableType
): ReactiveAdapter? = resolvableType.resolve()?.let(this::getAdapter)

internal fun ReactiveAdapterRegistry.resolveElementType(parameter: MethodParameter): Class<*>? {
  val targetType = ResolvableType.forMethodParameter(parameter)
  val adapter = resolveAdapter(targetType)
  val elementType = if (adapter != null) targetType.getGeneric() else targetType
  return elementType.resolve()
}
