package com.caplin.integration.datasourcex.reactive.api

sealed interface ContainerEvent<out T : Any> {

  /** Send a number of updates in one. This may be more efficient on the wire. */
  class Bulk<out T : Any>(val events: List<RowEvent<T>>) : ContainerEvent<T> {
    operator fun component1(): List<RowEvent<T>> = events

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Bulk<*>

      return events == other.events
    }

    override fun hashCode(): Int {
      return events.hashCode()
    }

    override fun toString(): String {
      return "Bulk(events=$events)"
    }
  }

  sealed interface RowEvent<out T : Any> : ContainerEvent<T> {
    val key: String

    operator fun component1(): String = key

    /** Insert or replace an existing row */
    class Upsert<out T : Any>(override val key: String, val value: T) : RowEvent<T> {

      operator fun component2(): T = value

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Upsert<*>

        if (key != other.key) return false
        if (value != other.value) return false

        return true
      }

      override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.hashCode()
        return result
      }

      override fun toString(): String {
        return "Upsert(key='$key', value=$value)"
      }
    }

    /** Remove an existing row */
    class Remove(override val key: String) : RowEvent<Nothing> {

      override fun toString(): String {
        return "Remove(key='$key')"
      }

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Remove

        return key == other.key
      }

      override fun hashCode(): Int {
        return key.hashCode()
      }
    }
  }
}
