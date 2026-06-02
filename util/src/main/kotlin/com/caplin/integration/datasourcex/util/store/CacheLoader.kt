package com.caplin.integration.datasourcex.util.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Read half of the store SPI. */
interface CacheLoader<K : Any, V : Any> {
  suspend fun load(key: K): Versioned<V>?

  suspend fun loadAll(keys: Collection<K>): Map<K, Versioned<V>> = buildMap {
    for (key in keys) load(key)?.let { put(key, it) }
  }

  /** Streams every known key, for warm-up / enumeration rather than the hot path. */
  fun loadAllKeys(): Flow<K> = emptyFlow()
}
