package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription

internal const val DEFAULT_SIGNAL_BUFFER: Int = 256

/**
 * Shared core for the store-backed [FlowStore] variants: a delta-only [signal] bus, a bounded hot
 * set [cache] of [CacheEntry] values, and read-through on a miss. Entries carry versions so each
 * entry's freshness can be compared against incoming deltas and concurrent read-through loads.
 */
internal abstract class AbstractFlowStore<K : Any, V : Any>(
    protected val loader: CacheLoader<K, V>,
    protected val cache: SuspendingCache<K, CacheEntry<V>>,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : FlowStore<K, V> {

  protected val signal =
      MutableSharedFlow<VersionedMapEvent<K, V>>(
          replay = 0,
          extraBufferCapacity = bufferCapacity,
          onBufferOverflow = BufferOverflow.SUSPEND,
      )

  override fun asFlow(): Flow<VersionedMapEvent<K, V>> = signal.asSharedFlow()

  override suspend fun get(key: K): V? =
      (cache.getIfPresent(key) ?: loadAndCache(key))?.valueOrNull()

  /**
   * Loads [key] through the store and caches it, but never regresses a fresher resident entry: a
   * delta stream can legitimately run ahead of the store a read-through reads from. A resident
   * [Tombstone] beats a null load, so a removed key is not re-read as absent.
   */
  protected open suspend fun loadAndCache(key: K): CacheEntry<V>? =
      loader.load(key)?.let { cachePutIfNewer(key, Live(it.value, it.version)) }
          ?: cache.getIfPresent(key)

  /**
   * Atomically caches [candidate] unless a strictly-newer entry is already resident, returning the
   * resident entry afterwards. The cache's per-key atomic compute closes the check-then-put race,
   * so a concurrent writer (commit, read-through, or inbound delta) cannot be clobbered by a stale
   * read.
   */
  protected fun cachePutIfNewer(key: K, candidate: CacheEntry<V>): CacheEntry<V> {
    val resident =
        cache.asyncCache().asMap().merge(key, CompletableFuture.completedFuture(candidate)) {
            oldFuture,
            newFuture ->
          val old = oldFuture.getNow(null)
          if (old != null && old.version >= candidate.version) oldFuture else newFuture
        }
    return resident?.getNow(candidate) ?: candidate
  }

  /**
   * Reflects [event] onto a *resident* entry only, gating on version — the consumer counterpart to
   * [cachePutIfNewer], which also inserts. A removal leaves a tombstone so a stale older
   * read-through is rejected by version; non-resident keys are left for the next read-through.
   */
  protected fun cacheReflectIfNewer(event: VersionedMapEvent<K, V>) {
    cache.asyncCache().asMap().computeIfPresent(event.key) { _, oldFuture ->
      val old = oldFuture.getNow(null)
      if (old != null && event.version > old.version)
          CompletableFuture.completedFuture(event.toEntry())
      else oldFuture
    }
  }

  override fun valueFlow(key: K): Flow<V?> =
      flow {
            var highest = Long.MIN_VALUE
            signal
                .onSubscription {
                  val initial = loadAndCache(key)
                  highest = initial?.version ?: Long.MIN_VALUE
                  this@flow.emit(initial?.valueOrNull())
                }
                .collect { event ->
                  if (event.key == key && event.version > highest) {
                    highest = event.version
                    this@flow.emit(event.valueOrNull())
                  }
                }
          }
          .distinctUntilChanged()
}

private fun <V> CacheEntry<V>.valueOrNull(): V? =
    when (this) {
      is Live -> value
      is Tombstone -> null
    }

internal fun <V : Any> VersionedMapEvent<*, V>.valueOrNull(): V? =
    when (this) {
      is VersionedMapEvent.Upsert -> value
      is VersionedMapEvent.Removed -> null
    }

internal fun <V : Any> VersionedMapEvent<*, V>.toEntry(): CacheEntry<V> =
    when (this) {
      is VersionedMapEvent.Upsert -> Live(value, version)
      is VersionedMapEvent.Removed -> Tombstone(version)
    }
