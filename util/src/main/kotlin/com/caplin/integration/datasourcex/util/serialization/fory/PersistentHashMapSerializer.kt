package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.collection.MapSerializer

/** A Fory [MapSerializer] for [PersistentMap] (Hash implementation). */
internal class PersistentHashMapSerializer(fory: Fory, type: Class<PersistentMap<*, *>>) :
    MapSerializer<PersistentMap<*, *>>(fory, type, true) {

  override fun newMap(buffer: MemoryBuffer): MutableMap<*, *> {
    val numElements = buffer.readVarUint32Small7()
    setNumElements(numElements)
    val map = HashMap<Any?, Any?>(numElements)
    refResolver.reference(map)
    return map
  }

  override fun newMap(map: Map<*, *>): MutableMap<*, *> {
    return HashMap<Any?, Any?>(map.size)
  }

  @Suppress("UNCHECKED_CAST")
  override fun onMapRead(map: Map<*, *>): PersistentMap<*, *> {
    return (map as Map<Any, Any>).toPersistentHashMap()
  }

  @Suppress("UNCHECKED_CAST")
  override fun onMapCopy(map: Map<*, *>): PersistentMap<*, *> {
    return (map as Map<Any, Any>).toPersistentHashMap()
  }
}
