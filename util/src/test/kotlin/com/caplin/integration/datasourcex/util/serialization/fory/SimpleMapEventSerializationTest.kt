package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.fory.Fory
import org.apache.fory.config.Language

class SimpleMapEventSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .build()
              .registerDataSourceSerializers()

      test("Populated") {
        val event = SimpleMapEvent.Populated
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      test("Upsert") {
        val event = SimpleMapEvent.EntryEvent.Upsert("key", "value")
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      test("Removed") {
        val event = SimpleMapEvent.EntryEvent.Removed<String, String>("key")
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      context("EntryEvent specifically") {
        test("Upsert") {
          val event: SimpleMapEvent.EntryEvent<String, String> =
              SimpleMapEvent.EntryEvent.Upsert("key", "value")
          val bytes = fory.serialize(event)
          val deserialized = fory.deserialize(bytes)
          deserialized shouldBe event
        }

        test("Removed") {
          val event: SimpleMapEvent.EntryEvent<String, String> =
              SimpleMapEvent.EntryEvent.Removed("key")
          val bytes = fory.serialize(event)
          val deserialized = fory.deserialize(bytes)
          deserialized shouldBe event
        }
      }
    })
