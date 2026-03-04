package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.collection.MapSerializer

/** A Fory [MapSerializer] for [PersistentMap] (Ordered implementation). */
internal class PersistentOrderedMapSerializer(fory: Fory, type: Class<PersistentMap<*, *>>) :
    MapSerializer<PersistentMap<*, *>>(fory, type) {

  override fun write(buffer: MemoryBuffer, value: PersistentMap<*, *>) {
    fory.writeRef(buffer, LinkedHashMap(value))
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(buffer: MemoryBuffer): PersistentMap<*, *> {
    val map = fory.readRef(buffer) as Map<Any, Any>
    return map.toPersistentMap()
  }

  override fun newMap(buffer: MemoryBuffer): MutableMap<*, *> {
    return LinkedHashMap<Any, Any>()
  }

  @Suppress("UNCHECKED_CAST")
  override fun onMapRead(map: Map<*, *>): PersistentMap<*, *> {
    // toPersistentMap() on a LinkedHashMap preserves order in kotlinx-collections-immutable
    return (map as Map<Any, Any>).toPersistentMap()
  }
}
