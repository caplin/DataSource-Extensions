package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.SetEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.fory.Fory
import org.apache.fory.config.Language

class SetEventSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .build()
              .registerDataSourceSerializers()

      test("Populated") {
        val event = SetEvent.Populated
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      test("Insert") {
        val event = SetEvent.EntryEvent.Insert("value")
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      test("Removed") {
        val event = SetEvent.EntryEvent.Removed("value")
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }
    })
