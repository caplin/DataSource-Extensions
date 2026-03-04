package com.caplin.integration.datasourcex.util.flow

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import org.apache.fory.Fory
import org.apache.fory.serializer.collection.MapSerializer

/**
 * Registers serializers for [PersistentMap] and other internal types with the provided [Fory]
 * instance.
 */
fun Fory.registerDataSourceSerializers(): Fory = apply {
  registerSerializer(PersistentMap::class.java, PersistentMapSerializer::class.java)
  // Kotlin immutable collections implementations often have internal names
  val hashMapClass =
      runCatching {
            Class.forName(
                "kotlinx.collections.immutable.implementations.immutableMap.PersistentHashMap"
            )
          }
          .getOrNull()
  if (hashMapClass != null) {
    registerSerializer(hashMapClass, PersistentMapSerializer::class.java)
  }
}

/** A Fory [MapSerializer] for [PersistentMap]. */
class PersistentMapSerializer(fory: Fory, type: Class<PersistentMap<*, *>>) :
    MapSerializer<PersistentMap<*, *>>(fory, type) {

  @Suppress("UNCHECKED_CAST")
  override fun onMapRead(map: Map<*, *>): PersistentMap<*, *> {
    return (map as Map<Any, Any>).toPersistentMap()
  }
}
