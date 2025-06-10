package com.caplin.integration.datasourcex.util

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentSetOf

fun <E> PersistentSet<E>.serializable(): PersistentSet<E> = SerializablePersistentSet(this)

internal class SerializablePersistentSet<E>(@Transient private var set: PersistentSet<E>) :
    PersistentSet<E>, Serializable {

  companion object {
    private const val serialVersionUID: Long = 1203740006547290238L
  }

  override val size: Int
    get() = set.size

  override fun builder(): PersistentSet.Builder<E> = set.builder()

  override fun clear(): PersistentSet<E> = set.clear()

  override fun addAll(elements: Collection<E>): PersistentSet<E> = set.addAll(elements)

  override fun add(element: E): PersistentSet<E> = set.add(element)

  override fun isEmpty(): Boolean = set.isEmpty()

  override fun iterator(): Iterator<E> = set.iterator()

  override fun retainAll(elements: Collection<E>): PersistentSet<E> = set.retainAll(elements)

  override fun removeAll(elements: Collection<E>): PersistentSet<E> = set.removeAll(elements)

  override fun removeAll(predicate: (E) -> Boolean): PersistentSet<E> = set.removeAll(predicate)

  override fun remove(element: E): PersistentSet<E> = set.remove(element)

  override fun containsAll(elements: Collection<E>): Boolean = set.containsAll(elements)

  override fun contains(element: E): Boolean = set.contains(element)

  override fun toString(): String = set.toString()

  @Suppress("unused")
  private fun writeObject(out: ObjectOutputStream) {
    out.writeInt(set.size)
    set.forEach { out.writeObject(it) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun readObject(input: ObjectInputStream) {
    set =
        persistentSetOf<E>().mutate {
          repeat(input.readInt()) { _ -> it.add(input.readObject() as E) }
        }
  }
}
