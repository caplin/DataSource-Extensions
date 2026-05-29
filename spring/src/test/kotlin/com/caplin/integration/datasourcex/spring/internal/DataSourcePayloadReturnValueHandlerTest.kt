package com.caplin.integration.datasourcex.spring.internal

import com.caplin.integration.datasourcex.spring.internal.DataSourcePayloadReturnValueHandler.Companion.RESPONSE_HEADER
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.messaging.support.MessageBuilder

internal class DataSourcePayloadReturnValueHandlerTest :
    FunSpec({
      val handler =
          DataSourcePayloadReturnValueHandler(ReactiveAdapterRegistry.getSharedInstance())

      fun returnType(methodName: String) =
          MethodParameter(ReturnValues::class.java.getMethod(methodName), -1)

      fun message(ref: AtomicReference<Flow<Any>>?) =
          MessageBuilder.withPayload("ignored")
              .apply { ref?.let { setHeader(RESPONSE_HEADER, it) } }
              .build()

      fun collect(ref: AtomicReference<Flow<Any>>) = runBlocking { ref.get().toList() }

      test("a single value is exposed as a one-element flow") {
        val ref = AtomicReference<Flow<Any>>()
        handler.handleReturnValue("hello", returnType("value"), message(ref)).block()
        collect(ref) shouldBe listOf("hello")
      }

      test("a null return value is exposed as an empty flow") {
        val ref = AtomicReference<Flow<Any>>()
        handler.handleReturnValue(null, returnType("value"), message(ref)).block()
        collect(ref) shouldBe emptyList()
      }

      test("a returned Flow is exposed element by element") {
        val ref = AtomicReference<Flow<Any>>()
        handler
            .handleReturnValue(flowOf("a", "b", "c"), returnType("flow"), message(ref))
            .block()
        collect(ref) shouldBe listOf("a", "b", "c")
      }

      test("a missing response-reference header fails") {
        shouldThrow<IllegalStateException> {
          handler.handleReturnValue("hello", returnType("value"), message(null)).block()
        }
      }
    })

private class ReturnValues {
  fun value(): Any = "ignored"

  fun flow(): Flow<Any> = flowOf()
}
