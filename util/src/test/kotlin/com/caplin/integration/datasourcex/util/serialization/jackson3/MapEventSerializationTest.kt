package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonMapperBuilder

class MapEventSerializationTest :
    FunSpec({
      val mapper = jacksonMapperBuilder().addDataSourceModule().build()

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

      context("EntryEvent specifically") {
        test("Upsert") {
          val event: MapEvent.EntryEvent<String, String> = Upsert("key", "old", "new")
          val json = mapper.writeValueAsString(event)
          val deserialized: MapEvent.EntryEvent<String, String> =
              mapper.readValue(
                  json,
                  object : TypeReference<MapEvent.EntryEvent<String, String>>() {},
              )
          deserialized shouldBe event
        }

        test("Removed") {
          val event: MapEvent.EntryEvent<String, String> = Removed("key", "old")
          val json = mapper.writeValueAsString(event)
          val deserialized: MapEvent.EntryEvent<String, String> =
              mapper.readValue(
                  json,
                  object : TypeReference<MapEvent.EntryEvent<String, String>>() {},
              )
          deserialized shouldBe event
        }
      }
    })
