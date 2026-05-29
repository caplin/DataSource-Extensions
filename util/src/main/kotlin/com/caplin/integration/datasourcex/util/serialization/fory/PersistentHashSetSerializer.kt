package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentHashSet
import org.apache.fory.context.CopyContext
import org.apache.fory.context.ReadContext
import org.apache.fory.resolver.TypeResolver
import org.apache.fory.serializer.collection.CollectionSerializer

/** A Fory [CollectionSerializer] for [PersistentSet] (Hash implementation). */
internal class PersistentHashSetSerializer(
    typeResolver: TypeResolver,
    type: Class<PersistentSet<*>>,
) : CollectionSerializer<PersistentSet<*>>(typeResolver, type, true) {

  override fun newCollection(readContext: ReadContext): MutableCollection<*> {
    val numElements = readCollectionSize(readContext.buffer)
    setNumElements(numElements)
    val set = HashSet<Any?>(numElements)
    readContext.reference(set)
    return set
  }

  override fun newCollection(
      copyContext: CopyContext,
      collection: Collection<*>,
  ): MutableCollection<*> {
    return HashSet<Any?>(collection.size)
  }

  @Suppress("UNCHECKED_CAST")
  override fun onCollectionRead(collection: Collection<*>): PersistentSet<*> {
    return (collection as Collection<Any>).toPersistentHashSet()
  }
}
