package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.SetEvent
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SetEventSerializationTest :
    FunSpec({
      val mapper: ObjectMapper = jacksonObjectMapper().registerDataSourceModule()

      test("Populated") {
        val event: SetEvent<String> = SetEvent.Populated
        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, object : TypeReference<SetEvent<String>>() {})
        deserialized shouldBe SetEvent.Populated
      }

      test("Insert") {
        val event: SetEvent<String> = SetEvent.EntryEvent.Insert("value")
        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, object : TypeReference<SetEvent<String>>() {})
        deserialized shouldBe event
      }

      test("Removed") {
        val event: SetEvent<String> = SetEvent.EntryEvent.Removed("value")
        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, object : TypeReference<SetEvent<String>>() {})
        deserialized shouldBe event
      }

      context("EntryEvent specifically") {
        test("Insert") {
          val event: SetEvent.EntryEvent<String> = SetEvent.EntryEvent.Insert("value")
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(json, object : TypeReference<SetEvent.EntryEvent<String>>() {})
          deserialized shouldBe event
        }

        test("Removed") {
          val event: SetEvent.EntryEvent<String> = SetEvent.EntryEvent.Removed("value")
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(json, object : TypeReference<SetEvent.EntryEvent<String>>() {})
          deserialized shouldBe event
        }
      }
    })
