package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.persistentMapOf
import org.apache.fory.Fory
import org.apache.fory.config.Language

class FlowMapStreamEventSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .build()
              .registerDataSourceSerializers()

      test("InitialState (raw PersistentMap)") {
        val map = persistentMapOf("1" to "A", "2" to "B")
        val initialState = FlowMapStreamEvent.InitialState(map)

        val bytes = fory.serialize(initialState)
        val deserialized = fory.deserialize(bytes)

        deserialized.shouldBeInstanceOf<FlowMapStreamEvent.InitialState<String, String>>()
        deserialized.map shouldContainExactly mapOf("1" to "A", "2" to "B")
      }

      test("EventUpdate") {
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

      test("InitialState with PersistentOrderedMap") {
        val map = persistentMapOf("Z" to 1, "A" to 2, "M" to 3)
        val initialState = FlowMapStreamEvent.InitialState(map)

        val bytes = fory.serialize(initialState)
        val deserialized = fory.deserialize(bytes)

        deserialized.shouldBeInstanceOf<FlowMapStreamEvent.InitialState<String, Int>>()
        deserialized.map shouldContainExactly mapOf("Z" to 1, "A" to 2, "M" to 3)
        deserialized.map.keys.toList() shouldBe listOf("Z", "A", "M")
      }
    })
