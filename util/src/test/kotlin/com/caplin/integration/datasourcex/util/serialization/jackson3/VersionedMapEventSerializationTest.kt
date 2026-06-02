package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonMapperBuilder

class VersionedMapEventSerializationTest :
    FunSpec({
      val mapper = jacksonMapperBuilder().addDataSourceModule().build()

      test("Upsert") {
        val event: VersionedMapEvent<String, String> = VersionedMapEvent.Upsert("key", "value", 7L)
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<VersionedMapEvent<String, String>>() {})
        deserialized shouldBe event
      }

      test("Removed") {
        val event: VersionedMapEvent<String, String> = VersionedMapEvent.Removed("key", 9L)
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<VersionedMapEvent<String, String>>() {})
        deserialized shouldBe event
      }
    })
