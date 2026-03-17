package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.mutableFlowMapOf
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FlowMapStreamEventSerializationTest :
    FunSpec({
      val mapper: ObjectMapper = jacksonObjectMapper().registerDataSourceModule()

      test("InitialState") {
        val map = mutableFlowMapOf("1" to "A", "2" to "B")
        val initialState: FlowMapStreamEvent<String, String> =
            FlowMapStreamEvent.InitialState(map.asMap())

        val json = mapper.writeValueAsString(initialState)
        val deserialized: FlowMapStreamEvent<String, String> =
            mapper.readValue(
                json,
                object : TypeReference<FlowMapStreamEvent<String, String>>() {},
            )

        deserialized.shouldBeInstanceOf<FlowMapStreamEvent.InitialState<String, String>>()
        deserialized.map shouldContainExactly mapOf("1" to "A", "2" to "B")
      }

      test("EventUpdate") {
        val event: FlowMapStreamEvent<String, String> =
            FlowMapStreamEvent.EventUpdate(Upsert("3", "B", "C"))

        val json = mapper.writeValueAsString(event)
        val deserialized: FlowMapStreamEvent<String, String> =
            mapper.readValue(
                json,
                object : TypeReference<FlowMapStreamEvent<String, String>>() {},
            )

        deserialized.shouldBeInstanceOf<FlowMapStreamEvent.EventUpdate<String, String>>()
        deserialized.event.shouldBeInstanceOf<Upsert<String, String>>()
        val upsert = deserialized.event
        upsert.key shouldBe "3"
        upsert.oldValue shouldBe "B"
        upsert.newValue shouldBe "C"
      }
    })
