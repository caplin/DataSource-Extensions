package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import org.apache.fory.context.CopyContext
import org.apache.fory.context.ReadContext
import org.apache.fory.resolver.TypeResolver
import org.apache.fory.serializer.collection.MapSerializer

/** A Fory [MapSerializer] for [PersistentMap] (Ordered implementation). */
internal class PersistentOrderedMapSerializer(
    typeResolver: TypeResolver,
    type: Class<PersistentMap<*, *>>,
) : MapSerializer<PersistentMap<*, *>>(typeResolver, type, true) {

  override fun newMap(readContext: ReadContext): MutableMap<*, *> {
    val numElements = readMapSize(readContext.buffer)
    setNumElements(numElements)
    val map = LinkedHashMap<Any?, Any?>(numElements)
    readContext.reference(map)
    return map
  }

  override fun newMap(copyContext: CopyContext, map: Map<*, *>): MutableMap<*, *> {
    return LinkedHashMap<Any?, Any?>(map.size)
  }

  @Suppress("UNCHECKED_CAST")
  override fun onMapRead(map: Map<*, *>): PersistentMap<*, *> {
    return (map as Map<Any, Any>).toPersistentMap()
  }

  @Suppress("UNCHECKED_CAST")
  override fun onMapCopy(map: Map<*, *>): PersistentMap<*, *> {
    return (map as Map<Any, Any>).toPersistentMap()
  }
}
