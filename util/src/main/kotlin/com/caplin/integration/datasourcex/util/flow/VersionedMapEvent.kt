package com.caplin.integration.datasourcex.util.flow

/**
 * A map mutation carrying the [version] it was written at, for distribution to consumers that gate
 * on `version >`. Unlike [MapEvent] there is no old value and no `Populated` marker: the stream is
 * delta-only and current values are read from the store.
 */
sealed interface VersionedMapEvent<out K : Any, out V : Any> {
  val key: K
  val version: Long

  class Upsert<out K : Any, out V : Any>(
      override val key: K,
      val value: V,
      override val version: Long,
  ) : VersionedMapEvent<K, V> {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Upsert<*, *>

      if (key != other.key) return false
      if (value != other.value) return false
      if (version != other.version) return false

      return true
    }

    override fun hashCode(): Int {
      var result = key.hashCode()
      result = 31 * result + value.hashCode()
      result = 31 * result + version.hashCode()
      return result
    }

    override fun toString(): String = "Upsert(key=$key, value=$value, version=$version)"
  }

  class Removed<out K : Any>(override val key: K, override val version: Long) :
      VersionedMapEvent<K, Nothing> {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Removed<*>

      if (key != other.key) return false
      if (version != other.version) return false

      return true
    }

    override fun hashCode(): Int {
      var result = key.hashCode()
      result = 31 * result + version.hashCode()
      return result
    }

    override fun toString(): String = "Removed(key=$key, version=$version)"
  }
}
