package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MapEventSerializationTest :
    FunSpec({
      val mapper: ObjectMapper = jacksonObjectMapper().registerDataSourceModule()

      test("Populated") {
        val event: MapEvent<String, String> = MapEvent.Populated
        val json = mapper.writeValueAsString(event)
        val deserialized: MapEvent<String, String> =
            mapper.readValue(json, object : TypeReference<MapEvent<String, String>>() {})
        deserialized shouldBe MapEvent.Populated
      }

      test("Upsert") {
        val event: MapEvent<String, String> = Upsert("key", "old", "new")
        val json = mapper.writeValueAsString(event)
        val deserialized: MapEvent<String, String> =
            mapper.readValue(json, object : TypeReference<MapEvent<String, String>>() {})
        deserialized shouldBe event
      }

      test("Removed") {
        val event: MapEvent<String, String> = Removed("key", "old")
        val json = mapper.writeValueAsString(event)
        val deserialized: MapEvent<String, String> =
            mapper.readValue(json, object : TypeReference<MapEvent<String, String>>() {})
        deserialized shouldBe event
      }
    })
