package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

/**
 * Builds a [FlowStoreCache] from a caller-supplied [Caffeine] spec. The entry type is internal, so
 * the public factories take the untyped builder and stamp the value type here. The value is
 * nullable so a read-through miss can cache nothing (Caffeine's compute skips a null result).
 */
@Suppress("UNCHECKED_CAST")
internal fun <K : Any, V : Any> Caffeine<Any, Any>.buildFlowStoreCache(): FlowStoreCache<K, V> =
    FlowStoreCache(build<K, CacheEntry<V>?>())

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
   * Applies [event] to the cache, gated on version: a strictly-newer delta updates a resident entry
   * or seeds an absent one. Seeding absent keys keeps the cache consistent with the stream the
   * consumer has already observed, so a later read-through can never regress to a value older than
   * a delta the consumer saw (e.g. a lagging replica read). The [Caffeine] size bound still caps
   * the hot set, evicting cold keys as deltas for them arrive.
   */
  fun reflectIfNewer(event: VersionedMapEvent<K, V>) {
    putIfNewer(event.key, event.toEntry())
  }
}
