package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.persistentMapOf
import org.apache.fory.Fory
import org.apache.fory.config.Language

class FlowMapSerializationTest :
    FunSpec({

      context("Jackson Serialization") {
        val mapper: ObjectMapper = jacksonObjectMapper()
            .registerDataSourceModule()

        test("serialize and deserialize InitialState") {
          val map = mutableFlowMapOf("1" to "A", "2" to "B")
          val initialState: FlowMapStreamEvent<String, String> =
              FlowMapStreamEvent.InitialState(map.asMap())

          val json = mapper.writeValueAsString(initialState)
          val deserialized: FlowMapStreamEvent<String, String> =
              mapper.readValue(json, object : TypeReference<FlowMapStreamEvent<String, String>>() {})

          deserialized.shouldBeInstanceOf<FlowMapStreamEvent.InitialState<String, String>>()
          deserialized.map shouldContainExactly mapOf("1" to "A", "2" to "B")
        }

        test("serialize and deserialize EventUpdate") {
          val event: FlowMapStreamEvent<String, String> =
              FlowMapStreamEvent.EventUpdate(Upsert("3", "B", "C"))

          val json = mapper.writeValueAsString(event)
          val deserialized: FlowMapStreamEvent<String, String> =
              mapper.readValue(json, object : TypeReference<FlowMapStreamEvent<String, String>>() {})

          deserialized.shouldBeInstanceOf<FlowMapStreamEvent.EventUpdate<String, String>>()
          deserialized.event.shouldBeInstanceOf<Upsert<String, String>>()
          val upsert = deserialized.event as Upsert
          upsert.key shouldBe "3"
          upsert.oldValue shouldBe "B"
          upsert.newValue shouldBe "C"
        }

        test("serialize and deserialize MapEvent.Populated") {
          val event: MapEvent<String, String> = MapEvent.Populated

          val json = mapper.writeValueAsString(event)
          val deserialized: MapEvent<String, String> =
              mapper.readValue(json, object : TypeReference<MapEvent<String, String>>() {})

          deserialized shouldBe MapEvent.Populated
        }
      }

      context("Apache Fory Serialization") {
        val fory =
            Fory.builder().withLanguage(Language.JAVA).requireClassRegistration(false).build()
                .registerDataSourceSerializers()

        test("serialize and deserialize InitialState (raw PersistentMap)") {
          val map = persistentMapOf("1" to "A", "2" to "B")
          val initialState = FlowMapStreamEvent.InitialState(map)

          val bytes = fory.serialize(initialState)
          val deserialized = fory.deserialize(bytes)

          deserialized.shouldBeInstanceOf<FlowMapStreamEvent.InitialState<String, String>>()
          deserialized.map shouldContainExactly mapOf("1" to "A", "2" to "B")
        }

        test("serialize and deserialize EventUpdate") {
          val event = FlowMapStreamEvent.EventUpdate(Upsert("3", "B", "C"))

          val bytes = fory.serialize(event)
          val deserialized = fory.deserialize(bytes)

          deserialized.shouldBeInstanceOf<FlowMapStreamEvent.EventUpdate<String, String>>()
          deserialized.event.shouldBeInstanceOf<Upsert<String, String>>()
          val upsert = deserialized.event as Upsert<*, *>
          upsert.key shouldBe "3"
          upsert.oldValue shouldBe "B"
          upsert.newValue shouldBe "C"
        }
      }
    })
