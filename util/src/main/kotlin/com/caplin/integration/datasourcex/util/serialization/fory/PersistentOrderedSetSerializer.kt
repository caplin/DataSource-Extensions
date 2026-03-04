package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentSet
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.collection.CollectionSerializer

/** A Fory [CollectionSerializer] for [PersistentSet] (Ordered implementation). */
internal class PersistentOrderedSetSerializer(fory: Fory, type: Class<PersistentSet<*>>) :
    CollectionSerializer<PersistentSet<*>>(fory, type) {

  override fun write(buffer: MemoryBuffer, value: PersistentSet<*>) {
    fory.writeRef(buffer, LinkedHashSet(value))
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(buffer: MemoryBuffer): PersistentSet<*> {
    val collection = fory.readRef(buffer) as Collection<Any>
    return collection.toPersistentSet()
  }

  override fun newCollection(buffer: MemoryBuffer): MutableCollection<*> {
    return LinkedHashSet<Any>()
  }

  @Suppress("UNCHECKED_CAST")
  override fun onCollectionRead(collection: Collection<*>): PersistentSet<*> {
    return (collection as Collection<Any>).toPersistentSet()
  }
}
