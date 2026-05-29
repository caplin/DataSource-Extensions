package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import org.apache.fory.context.CopyContext
import org.apache.fory.context.ReadContext
import org.apache.fory.resolver.TypeResolver
import org.apache.fory.serializer.collection.MapSerializer

/** A Fory [MapSerializer] for [PersistentMap] (Hash implementation). */
internal class PersistentHashMapSerializer(
    typeResolver: TypeResolver,
    type: Class<PersistentMap<*, *>>,
) : MapSerializer<PersistentMap<*, *>>(typeResolver, type, true) {

  override fun newMap(readContext: ReadContext): MutableMap<*, *> {
    val numElements = readMapSize(readContext.buffer)
    setNumElements(numElements)
    val map = HashMap<Any?, Any?>(numElements)
    readContext.reference(map)
    return map
  }

  override fun newMap(copyContext: CopyContext, map: Map<*, *>): MutableMap<*, *> {
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
