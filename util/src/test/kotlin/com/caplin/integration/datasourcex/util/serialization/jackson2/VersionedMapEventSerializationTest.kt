package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VersionedMapEventSerializationTest :
    FunSpec({
      val mapper: ObjectMapper = jacksonObjectMapper().registerDataSourceModule()

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

      test("a non-integral version is rejected") {
        val json = """{"type":"upsert","key":"key","value":"value","version":"7"}"""
        shouldThrow<JsonMappingException> {
          mapper.readValue(json, object : TypeReference<VersionedMapEvent<String, String>>() {})
        }
      }

      test("an explicit-null version is rejected") {
        val json = """{"type":"upsert","key":"key","value":"value","version":null}"""
        shouldThrow<JsonMappingException> {
          mapper.readValue(json, object : TypeReference<VersionedMapEvent<String, String>>() {})
        }
      }
    })
