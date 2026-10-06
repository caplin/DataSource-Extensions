package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.apache.fory.context.CopyContext
import org.apache.fory.context.ReadContext
import org.apache.fory.resolver.TypeResolver
import org.apache.fory.serializer.collection.CollectionSerializer

/**
 * A Fory [CollectionSerializer] reading a collection back as a [PersistentList], for a persistent
 * map's `values` view, which is a collection rather than a set and has no public type of its own.
 */
internal class PersistentListSerializer(
    typeResolver: TypeResolver,
    type: Class<PersistentList<*>>,
) : CollectionSerializer<PersistentList<*>>(typeResolver, type, true) {

  override fun newCollection(
      readContext: ReadContext,
      elementReadAlwaysAdvances: Boolean,
  ): MutableCollection<*> {
    val numElements = readCollectionSize(readContext, readContext.buffer, elementReadAlwaysAdvances)
    setNumElements(numElements)
    val list = ArrayList<Any?>(numElements)
    readContext.reference(list)
    return list
  }

  override fun newCollection(
      copyContext: CopyContext,
      collection: Collection<*>,
  ): MutableCollection<*> {
    return ArrayList<Any?>(collection.size)
  }

  @Suppress("UNCHECKED_CAST")
  override fun onCollectionRead(collection: Collection<*>): PersistentList<*> {
    return (collection as Collection<Any>).toPersistentList()
  }
}
