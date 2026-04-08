package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.caplin.integration.datasourcex.util.flow.SetEvent
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import org.apache.fory.Fory

/** Registers serializers for internal types with the provided [Fory] instance. */
@Suppress("UNCHECKED_CAST")
fun Fory.registerDataSourceSerializers(preserveExceptionTypes: Boolean = false): Fory = apply {
  if (preserveExceptionTypes)
      check(config.trackingRef()) {
        "Tracking references must be enabled for exception types preservation"
      }

  // Register serializers for FlowMapStreamEvent value classes
  registerSerializer(FlowMapStreamEvent::class.java, FlowMapStreamEventSerializer::class.java)
  registerSerializer(
      FlowMapStreamEvent.InitialState::class.java,
      InitialStateSerializer::class.java,
  )
  registerSerializer(
      FlowMapStreamEvent.EventUpdate::class.java,
      EventUpdateSerializer::class.java,
  )

  // Register serializers for sealed interfaces
  registerSerializer(MapEvent::class.java, MapEventSerializer::class.java)
  registerSerializer(SimpleMapEvent::class.java, SimpleMapEventSerializer::class.java)
  registerSerializer(SetEvent::class.java, SetEventSerializer::class.java)

  registerSerializer(
      ValueOrCompletion::class.java,
      ValueOrCompletionSerializer(
          this,
          ValueOrCompletion::class.java,
          preserveExceptionTypes,
      ),
  )
}

fun Fory.registerPersistentCollectionSerializers(): Fory = apply {
  // Register concrete serializers for the internal PersistentMap implementations
  runCatching {
        Class.forName(
            "kotlinx.collections.immutable.implementations.immutableMap.PersistentHashMap"
        )
      }
      .getOrNull()
      ?.let { registerSerializer(it, PersistentHashMapSerializer::class.java) }

  runCatching {
        Class.forName(
            "kotlinx.collections.immutable.implementations.persistentOrderedMap.PersistentOrderedMap"
        )
      }
      .getOrNull()
      ?.let { registerSerializer(it, PersistentOrderedMapSerializer::class.java) }

  // Register concrete serializers for the internal PersistentSet implementations
  runCatching {
        Class.forName(
            "kotlinx.collections.immutable.implementations.immutableSet.PersistentHashSet"
        )
      }
      .getOrNull()
      ?.let { registerSerializer(it, PersistentHashSetSerializer::class.java) }

  runCatching {
        Class.forName(
            "kotlinx.collections.immutable.implementations.persistentOrderedSet.PersistentOrderedSet"
        )
      }
      .getOrNull()
      ?.let { registerSerializer(it, PersistentOrderedSetSerializer::class.java) }
}
