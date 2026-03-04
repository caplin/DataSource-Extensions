package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.collection.MapSerializer

/** A Fory [MapSerializer] for [PersistentMap] (Hash implementation). */
internal class PersistentHashMapSerializer(fory: Fory, type: Class<PersistentMap<*, *>>) :
    MapSerializer<PersistentMap<*, *>>(fory, type) {

  override fun write(buffer: MemoryBuffer, value: PersistentMap<*, *>) {
    fory.writeRef(buffer, HashMap(value))
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(buffer: MemoryBuffer): PersistentMap<*, *> {
    val map = fory.readRef(buffer) as Map<Any, Any>
    return map.toPersistentHashMap()
  }

  override fun newMap(buffer: MemoryBuffer): MutableMap<*, *> {
    return HashMap<Any, Any>()
  }

  @Suppress("UNCHECKED_CAST")
  override fun onMapRead(map: Map<*, *>): PersistentMap<*, *> {
    return (map as Map<Any, Any>).toPersistentHashMap()
  }
}
