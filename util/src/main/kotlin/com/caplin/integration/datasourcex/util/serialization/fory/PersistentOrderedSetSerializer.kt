package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentSet
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.collection.CollectionSerializer

/** A Fory [CollectionSerializer] for [PersistentSet] (Ordered implementation). */
internal class PersistentOrderedSetSerializer(fory: Fory, type: Class<PersistentSet<*>>) :
    CollectionSerializer<PersistentSet<*>>(fory, type, true) {

  override fun newCollection(buffer: MemoryBuffer): MutableCollection<*> {
    val numElements = buffer.readVarUint32Small7()
    setNumElements(numElements)
    val set = LinkedHashSet<Any?>(numElements)
    refResolver.reference(set)
    return set
  }

  override fun newCollection(collection: Collection<*>): MutableCollection<*> {
    return LinkedHashSet<Any?>(collection.size)
  }

  @Suppress("UNCHECKED_CAST")
  override fun onCollectionRead(collection: Collection<*>): PersistentSet<*> {
    return (collection as Collection<Any>).toPersistentSet()
  }

  @Suppress("UNCHECKED_CAST")
  fun onCollectionCopy(collection: Collection<*>): PersistentSet<*> {
    return (collection as Collection<Any>).toPersistentSet()
  }
}
