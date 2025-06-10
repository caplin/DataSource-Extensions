package com.caplin.integration.datasourcex.util

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf

fun <K, V> PersistentMap<K, V>.serializable(): PersistentMap<K, V> = SerializablePersistentMap(this)

internal class SerializablePersistentMap<K, V>(@Transient private var map: PersistentMap<K, V>) :
    PersistentMap<K, V>, Serializable {

  private companion object {
    private const val serialVersionUID: Long = 5296450722117811345L
  }

  override val entries: ImmutableSet<Map.Entry<K, V>>
    get() = map.entries

  override val keys: ImmutableSet<K>
    get() = map.keys

  override val size: Int
    get() = map.size

  override val values: ImmutableCollection<V>
    get() = map.values

  override fun builder(): PersistentMap.Builder<K, V> = map.builder()

  override fun clear(): PersistentMap<K, V> = map.clear()

  override fun isEmpty(): Boolean = map.isEmpty()

  override fun remove(key: K, value: V): PersistentMap<K, V> = map.remove(key, value)

  override fun remove(key: K): PersistentMap<K, V> = map.remove(key)

  override fun putAll(m: Map<out K, V>): PersistentMap<K, V> = map.putAll(m)

  override fun put(key: K, value: V): PersistentMap<K, V> = map.put(key, value)

  override fun get(key: K): V? = map[key]

  override fun containsValue(value: V): Boolean = map.containsValue(value)

  override fun containsKey(key: K): Boolean = map.containsKey(key)

  override fun toString(): String = map.toString()

  @Suppress("unused")
  private fun writeObject(out: ObjectOutputStream) {
    out.writeInt(map.size)
    map.forEach {
      out.writeObject(it.key)
      out.writeObject(it.value)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun readObject(input: ObjectInputStream) {
    map =
        persistentMapOf<K, V>().mutate {
          repeat(input.readInt()) { _ -> it[input.readObject() as K] = input.readObject() as V }
        }
  }
}
