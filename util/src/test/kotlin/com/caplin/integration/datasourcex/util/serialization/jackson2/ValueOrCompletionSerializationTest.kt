package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ValueOrCompletionSerializationTest :
    FunSpec({
      val mapper: ObjectMapper = jacksonObjectMapper().registerDataSourceModule()

      test("Value") {
        val event: ValueOrCompletion<String> = ValueOrCompletion.Value("value")
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<ValueOrCompletion<String>>() {})
        deserialized shouldBe event
      }

      test("Completion (null)") {
        val event: ValueOrCompletion<String> = ValueOrCompletion.Completion(null)
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<ValueOrCompletion<String>>() {})
        deserialized.shouldBeInstanceOf<ValueOrCompletion.Completion>()
        deserialized.throwable shouldBe null
      }

      test("Completion (error)") {
        val event: ValueOrCompletion<String> =
            ValueOrCompletion.Completion(RuntimeException("error"))
        val json = mapper.writeValueAsString(event)
        val deserialized =
            mapper.readValue(json, object : TypeReference<ValueOrCompletion<String>>() {})
        deserialized.shouldBeInstanceOf<ValueOrCompletion.Completion>()
        deserialized.throwable?.message shouldBe "error"
      }

      context("Value and Completion specifically") {
        test("Value") {
          val event: ValueOrCompletion.Value<String> = ValueOrCompletion.Value("value")
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(json, object : TypeReference<ValueOrCompletion.Value<String>>() {})
          deserialized shouldBe event
        }

        test("Completion (null)") {
          val event: ValueOrCompletion.Completion = ValueOrCompletion.Completion(null)
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(json, object : TypeReference<ValueOrCompletion.Completion>() {})
          deserialized.shouldBeInstanceOf<ValueOrCompletion.Completion>()
          deserialized.throwable shouldBe null
        }

        test("Completion (error)") {
          val event: ValueOrCompletion.Completion =
              ValueOrCompletion.Completion(RuntimeException("error"))
          val json = mapper.writeValueAsString(event)
          val deserialized =
              mapper.readValue(json, object : TypeReference<ValueOrCompletion.Completion>() {})
          deserialized.shouldBeInstanceOf<ValueOrCompletion.Completion>()
          deserialized.throwable?.message shouldBe "error"
        }
      }
    })
