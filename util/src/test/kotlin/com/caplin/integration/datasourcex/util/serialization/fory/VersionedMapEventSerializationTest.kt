package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.fory.Fory
import org.apache.fory.config.Language

class VersionedMapEventSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .build()
              .registerDataSourceSerializers()

      test("Upsert") {
        val event = VersionedMapEvent.Upsert("key", "value", 7L)
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }

      test("Removed") {
        val event = VersionedMapEvent.Removed("key", 9L)
        val bytes = fory.serialize(event)
        val deserialized = fory.deserialize(bytes)
        deserialized shouldBe event
      }
    })
