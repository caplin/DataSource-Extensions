package com.caplin.integration.datasourcex.util.serialization.fory

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
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
    })
