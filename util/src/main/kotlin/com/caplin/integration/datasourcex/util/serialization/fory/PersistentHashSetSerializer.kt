package com.caplin.integration.datasourcex.util.serialization.fory

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentHashSet
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.collection.CollectionSerializer

/** A Fory [CollectionSerializer] for [PersistentSet] (Hash implementation). */
internal class PersistentHashSetSerializer(fory: Fory, type: Class<PersistentSet<*>>) :
    CollectionSerializer<PersistentSet<*>>(fory, type) {

  override fun write(buffer: MemoryBuffer, value: PersistentSet<*>) {
    fory.writeRef(buffer, HashSet(value))
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(buffer: MemoryBuffer): PersistentSet<*> {
    val collection = fory.readRef(buffer) as Collection<Any>
    return collection.toPersistentHashSet()
  }

  override fun newCollection(buffer: MemoryBuffer): MutableCollection<*> {
    return HashSet<Any>()
  }

  @Suppress("UNCHECKED_CAST")
  override fun onCollectionRead(collection: Collection<*>): PersistentSet<*> {
    return (collection as Collection<Any>).toPersistentHashSet()
  }
}
