package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SimpleMapEventSerializationTest :
    FunSpec({
      val mapper: ObjectMapper = jacksonObjectMapper().registerDataSourceModule()

      test("Populated") {
        val event: SimpleMapEvent<String, String> = SimpleMapEvent.Populated
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<SimpleMapEvent<String, String>>() {})
        deserialized shouldBe SimpleMapEvent.Populated
      }

      test("Upsert") {
        val event: SimpleMapEvent<String, String> = SimpleMapEvent.EntryEvent.Upsert("key", "value")
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<SimpleMapEvent<String, String>>() {})
        deserialized shouldBe event
      }

      test("Removed") {
        val event: SimpleMapEvent<String, String> = SimpleMapEvent.EntryEvent.Removed("key")
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<SimpleMapEvent<String, String>>() {})
        deserialized shouldBe event
      }

      context("EntryEvent specifically") {
        test("Upsert") {
          val event: SimpleMapEvent.EntryEvent<String, String> =
              SimpleMapEvent.EntryEvent.Upsert("key", "value")
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(
                  json,
                  object : TypeReference<SimpleMapEvent.EntryEvent<String, String>>() {},
              )
          deserialized shouldBe event
        }

        test("Removed") {
          val event: SimpleMapEvent.EntryEvent<String, String> =
              SimpleMapEvent.EntryEvent.Removed("key")
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(
                  json,
                  object : TypeReference<SimpleMapEvent.EntryEvent<String, String>>() {},
              )
          deserialized shouldBe event
        }
      }
    })
