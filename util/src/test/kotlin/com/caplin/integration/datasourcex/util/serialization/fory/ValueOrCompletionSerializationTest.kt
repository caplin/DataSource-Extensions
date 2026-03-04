package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.fory.Fory
import org.apache.fory.config.Language

class ValueOrCompletionSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
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
    })
