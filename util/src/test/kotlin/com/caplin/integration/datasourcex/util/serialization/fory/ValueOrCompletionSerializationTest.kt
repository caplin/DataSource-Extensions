package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.fory.Fory
import org.apache.fory.config.Language

class CustomException(val customMessage: String) : Exception(customMessage)

class ValueOrCompletionSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .withRefTracking(true)
              .build()
              .registerDataSourceSerializers(preserveExceptionTypes = true)

      val foryNoType =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .withRefTracking(false)
              .build()
              .registerDataSourceSerializers()

      test("Value") {
        val event = ValueOrCompletion.Value("value")
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      test("Completion") {
        val event = ValueOrCompletion.Completion(null)
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      context("Value and Completion specifically") {
        test("Value") {
          val event: ValueOrCompletion.Value<String> = ValueOrCompletion.Value("value")
          val bytes = fory.serialize(event)
          val deserialized = fory.deserialize(bytes)
          deserialized shouldBe event
        }

        test("Completion with custom exception (preserved type)") {
          val event: ValueOrCompletion.Completion =
              ValueOrCompletion.Completion(CustomException("aah"))
          val bytes = fory.serialize(event)
          val deserialized = fory.deserialize(bytes) as ValueOrCompletion.Completion
          val throwable = deserialized.throwable.shouldBeInstanceOf<CustomException>()
          throwable.customMessage shouldBe "aah"
        }

        test("Completion with exception (no type)") {
          val event: ValueOrCompletion.Completion =
              ValueOrCompletion.Completion(IllegalStateException("aah"))
          val bytes = foryNoType.serialize(event)
          val deserialized = foryNoType.deserialize(bytes) as ValueOrCompletion.Completion
          deserialized.throwable.shouldBeInstanceOf<RuntimeException>()
          deserialized.throwable?.message shouldBe "aah"
        }
      }
    })
