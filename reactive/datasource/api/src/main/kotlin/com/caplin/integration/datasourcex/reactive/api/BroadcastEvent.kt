package com.caplin.integration.datasourcex.reactive.api

class BroadcastEvent<out T : Any>(val path: String, val value: T) {
  @Deprecated("Renamed to path", ReplaceWith("path"))
  val subject: String
    get() = path

  operator fun component1(): String = path

  operator fun component2(): T = value

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BroadcastEvent<*>

    if (path != other.path) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }

  override fun toString(): String {
    return "BroadcastEvent(path='$path', value=$value)"
  }
}
