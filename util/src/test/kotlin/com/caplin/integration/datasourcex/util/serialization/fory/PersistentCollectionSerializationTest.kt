package com.caplin.integration.datasourcex.util.serialization.fory

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.apache.fory.Fory
import org.apache.fory.config.Language

class PersistentCollectionSerializationTest :
    FunSpec({
      val fory =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .build()
              .registerPersistentCollectionSerializers()

      test("PersistentHashMap") {
        val map = persistentHashMapOf("1" to "A", "2" to "B")
        val bytes = fory.serialize(map)
        val deserialized = fory.deserialize(bytes) as Map<String, String>
        deserialized shouldContainExactly mapOf("1" to "A", "2" to "B")
      }

      test("PersistentOrderedMap") {
        val map = persistentMapOf("Z" to 1, "A" to 2, "M" to 3)
        val bytes = fory.serialize(map)
        val deserialized = fory.deserialize(bytes) as Map<String, Int>
        deserialized shouldContainExactly mapOf("Z" to 1, "A" to 2, "M" to 3)
        deserialized.keys.toList() shouldContainExactly listOf("Z", "A", "M")
      }

      test("PersistentHashSet") {
        val set = persistentHashSetOf("1", "2")
        val bytes = fory.serialize(set)
        val deserialized = fory.deserialize(bytes) as Set<String>
        deserialized shouldContainExactly setOf("1", "2")
      }

      test("PersistentOrderedSet") {
        val set = persistentSetOf("Z", "A", "M")
        val bytes = fory.serialize(set)
        val deserialized = fory.deserialize(bytes) as Set<String>
        deserialized shouldContainExactly setOf("Z", "A", "M")
        deserialized.toList() shouldContainExactly listOf("Z", "A", "M")
      }

      // A map's views are internal types Fory writes but cannot construct again. Read in a field,
      // with
      // reference tracking on, as a service-to-service message is: each reads back as the
      // persistent
      // collection it stands for, in the map's order where it has one.
      val tracking =
          Fory.builder()
              .withLanguage(Language.JAVA)
              .requireClassRegistration(false)
              .withRefTracking(true)
              .withCompatible(false)
              .build()
              .registerPersistentCollectionSerializers()

      // Left to Fory, a view goes to its Java collection fallback, which writes the view's own
      // fields
      // (the map behind it) and failed to read back in caplin-one's services.
      test("a persistent map's views have serializers of their own") {
        val ordered = persistentMapOf("Z" to 1)
        val hashed = persistentHashMapOf("1" to 1)
        val expected =
            mapOf(
                ordered.keys to "PersistentOrderedSetSerializer",
                ordered.values to "PersistentListSerializer",
                hashed.keys to "PersistentHashSetSerializer",
                hashed.values to "PersistentListSerializer",
            )
        for ((view, serializer) in
            expected.entries.map { (view, name) -> view.javaClass to name }) {
          tracking.typeResolver.getSerializer(view)::class.java.simpleName shouldBe serializer
        }
      }

      test("PersistentOrderedMap keys and values") {
        val map = persistentMapOf("Z" to 1, "A" to 2, "M" to 1)
        val read = tracking.deserialize(tracking.serialize(Views(map.keys, map.values))) as Views
        read.keys.toList() shouldContainExactly listOf("Z", "A", "M")
        read.values.toList() shouldContainExactly listOf(1, 2, 1)
      }

      test("PersistentHashMap keys and values") {
        val map = persistentHashMapOf("1" to 1, "2" to 1)
        val read = tracking.deserialize(tracking.serialize(Views(map.keys, map.values))) as Views
        read.keys shouldContainExactly setOf("1", "2")
        read.values.toList() shouldContainExactly listOf(1, 1)
      }
    }) {

  data class Views(val keys: Set<String>, val values: Collection<Int>)
}
