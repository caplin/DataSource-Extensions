package com.caplin.integration.datasourcex.spring.internal

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.reactive.asFlow
import org.reactivestreams.Publisher
import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.ResolvableType
import org.springframework.http.server.reactive.ChannelSendOperator
import org.springframework.lang.Nullable
import org.springframework.messaging.Message
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodReturnValueHandler
import org.springframework.util.Assert
import reactor.core.publisher.Mono

internal class DataSourcePayloadReturnValueHandler(
    private val reactiveAdapterRegistry: ReactiveAdapterRegistry
) : HandlerMethodReturnValueHandler {

  companion object {
    const val RESPONSE_HEADER = "dataSourceResponse"
  }

  override fun supportsReturnType(returnType: MethodParameter): Boolean {
    return true
  }

  override fun handleReturnValue(
      returnValue: Any?,
      returnType: MethodParameter,
      message: Message<*>,
  ): Mono<Void> {
    if (returnValue == null) {
      val responseRef = getResponseReference(message)
      responseRef?.set(emptyFlow())
    }

    val content = getContent(returnValue, returnType)
    return ChannelSendOperator(content) { publisher -> handleContent(publisher.asFlow(), message) }
  }

  private fun getContent(@Nullable content: Any?, returnType: MethodParameter): Publisher<Any> {
    val returnValueType = ResolvableType.forMethodParameter(returnType)
    val adapter = reactiveAdapterRegistry.getAdapter(returnValueType.resolve(), content)
    return adapter?.run { toPublisher(content) } ?: Mono.justOrEmpty(content)
  }

  private fun handleContent(encodedContent: Flow<Any>, message: Message<*>): Mono<Void?> {
    val responseRef: AtomicReference<Flow<Any>>? = getResponseReference(message)
    checkNotNull(responseRef) { "Missing '$RESPONSE_HEADER'" }
    responseRef.set(encodedContent)
    return Mono.empty()
  }

  @Suppress("UNCHECKED_CAST")
  private fun getResponseReference(message: Message<*>): AtomicReference<Flow<Any>>? {
    val headerValue = message.headers[RESPONSE_HEADER]
    Assert.state(
        headerValue == null || headerValue is AtomicReference<*>,
        "Expected AtomicReference",
    )
    return headerValue as? AtomicReference<Flow<Any>>
  }
}
