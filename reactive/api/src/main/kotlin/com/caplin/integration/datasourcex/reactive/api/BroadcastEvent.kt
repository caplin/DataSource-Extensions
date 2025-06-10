package com.caplin.integration.datasourcex.reactive.api

class BroadcastEvent<out T : Any>(val subject: String, val value: T) {
  operator fun component1(): String = subject

  operator fun component2(): T = value

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BroadcastEvent<*>

    if (subject != other.subject) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = subject.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }

  override fun toString(): String {
    return "BroadcastEvent(subject='$subject', value=$value)"
  }
}
