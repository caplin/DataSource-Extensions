package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

/**
 * A [SuspendingCache] of versioned [CacheEntry] values that owns the store's version gating. The
 * Caffeine map only ever holds completed entries; concurrent read-through misses for a key are
 * coalesced through [inFlight] into a single load.
 */
class FlowStoreCache<K : Any, V : Any>(
    cache: AsyncCache<K, CacheEntry<V>>,
    scope: CoroutineScope,
) : SuspendingCache<K, CacheEntry<V>>(cache, scope) {

  private val inFlight = ConcurrentHashMap<K, CompletableFuture<CacheEntry<V>?>>()

  /**
   * Returns the resident entry, or single-flight loads it via [load] on a miss and caches it,
   * without regressing a fresher entry written meanwhile. A null load caches nothing.
   */
  suspend fun loadIfNewer(key: K, load: suspend (K) -> Versioned<V>?): CacheEntry<V>? {
    getIfPresent(key)?.let {
      return it
    }
    val mine = CompletableFuture<CacheEntry<V>?>()
    inFlight.putIfAbsent(key, mine)?.let {
      return it.await()
    }
    loaderScope.launch {
      try {
        val loaded = load(key)
        mine.complete(
            loaded?.let { putIfNewer(key, Live(it.value, it.version)) } ?: getIfPresent(key)
        )
      } catch (t: Throwable) {
        mine.completeExceptionally(t)
      } finally {
        inFlight.remove(key, mine)
      }
    }
    return mine.await()
  }

  /**
   * Atomically caches [candidate] unless a strictly-newer entry is already resident, returning the
   * resident entry. The per-key atomic compute closes the check-then-put race.
   */
  fun putIfNewer(key: K, candidate: CacheEntry<V>): CacheEntry<V> {
    val merged =
        asyncCache().asMap().merge(key, CompletableFuture.completedFuture(candidate)) { old, new ->
          val current = old.getNow(null)
          if (current != null && current.version >= candidate.version) old else new
        }
    return merged?.getNow(candidate) ?: candidate
  }

  /**
   * Applies [event] to a resident entry only, gated on version; absent keys await the next read.
   */
  fun reflectIfNewer(event: VersionedMapEvent<K, V>) {
    asyncCache().asMap().computeIfPresent(event.key) { _, old ->
      val current = old.getNow(null)
      if (current != null && event.version > current.version)
          CompletableFuture.completedFuture(event.toEntry())
      else old
    }
  }
}

/** Builds a [FlowStoreCache] from a configured Caffeine builder. */
fun <K : Any, V : Any> Caffeine<in K, in CacheEntry<V>>.buildFlowStoreCache(
    scope: CoroutineScope
): FlowStoreCache<K, V> = FlowStoreCache(buildAsync(), scope)
