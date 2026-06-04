package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.LoadingCache

/**
 * A synchronous Caffeine [Cache] of versioned [CacheEntry] values that owns the store's version
 * gating. A read-through miss loads single-flight under Caffeine's per-key compute; owner writes
 * and inbound deltas apply through version-gated [putIfNewer] / [reflectIfNewer].
 */
internal class FlowStoreCache<K : Any, V : Any>(private val cache: Cache<K, CacheEntry<V>?>) {

  init {
    // The store owns read-through loading; a LoadingCache's loader and refreshAfterWrite would
    // write entries that bypass version gating.
    require(cache !is LoadingCache<*, *>) { "Pass a plain Cache, not a LoadingCache." }
  }

  fun getIfPresent(key: K): CacheEntry<V>? = cache.getIfPresent(key)

  /**
   * Cache-first; single-flight loads via [load] on a miss. A null load caches nothing. The atomic
   * per-key compute coalesces concurrent misses and serialises loads against deltas for the key.
   */
  fun getOrLoad(key: K, load: (K) -> Versioned<V>?): CacheEntry<V>? =
      cache.get(key) { load(it)?.let { v -> Live(v.value, v.version) } }

  /** Caches [candidate] unless a newer entry is already resident; returns the resident entry. */
  fun putIfNewer(key: K, candidate: CacheEntry<V>): CacheEntry<V> =
      cache.asMap().merge(key, candidate) { old, new ->
        if (old.version >= candidate.version) old else new
      }!!

  /**
   * Applies [event] to a resident entry only, gated on version; absent keys await the next read.
   */
  fun reflectIfNewer(event: VersionedMapEvent<K, V>) {
    cache.asMap().computeIfPresent(event.key) { _, old ->
      if (event.version > old.version) event.toEntry() else old
    }
  }
}
