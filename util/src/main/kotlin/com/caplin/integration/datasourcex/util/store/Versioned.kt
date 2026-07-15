package com.caplin.integration.datasourcex.util.store

/** A [value] paired with the monotonically increasing [version] it was written at. */
class Versioned<out V : Any>(val value: V, val version: Long) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Versioned<*>

    if (value != other.value) return false
    if (version != other.version) return false

    return true
  }

  override fun hashCode(): Int {
    var result = value.hashCode()
    result = 31 * result + version.hashCode()
    return result
  }

  override fun toString(): String = "Versioned(value=$value, version=$version)"
}
