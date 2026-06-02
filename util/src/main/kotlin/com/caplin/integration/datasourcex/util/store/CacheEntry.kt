package com.caplin.integration.datasourcex.util.store

/**
 * A versioned cache entry. [Live] holds a present value; [Tombstone] marks a removed key so a
 * stale, older read-through is rejected by version instead of silently repopulating the value.
 */
sealed interface CacheEntry<out V> {
  val version: Long
}

data class Live<out V>(val value: V, override val version: Long) : CacheEntry<V>

data class Tombstone(override val version: Long) : CacheEntry<Nothing>
