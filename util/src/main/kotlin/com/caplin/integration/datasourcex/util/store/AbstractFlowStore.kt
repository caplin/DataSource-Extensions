package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription

/**
 * Shared core for the store-backed [FlowStore] variants: a delta-only [signal] bus, a bounded hot
 * set [cache] of [Versioned] values, and read-through on a miss. The cache holds versions so that
 * each entry's freshness can be compared against incoming deltas.
 */
internal abstract class AbstractFlowStore<K : Any, V : Any>(
    protected val loader: CacheLoader<K, V>,
    protected val cache: SuspendingCache<K, Versioned<V>>,
) : FlowStore<K, V> {

  protected val signal =
      MutableSharedFlow<VersionedMapEvent<K, V>>(
          replay = 0,
          extraBufferCapacity = Int.MAX_VALUE,
          onBufferOverflow = BufferOverflow.SUSPEND,
      )

  override fun asFlow(): Flow<VersionedMapEvent<K, V>> = signal.asSharedFlow()

  override suspend fun get(key: K): V? = cache.getIfPresent(key)?.value ?: loadAndCache(key)?.value

  /** Loads [key] from the store and populates the cache. */
  protected open suspend fun loadAndCache(key: K): Versioned<V>? =
      loader.load(key)?.also { cache.put(key, it) }

  override fun valueFlow(key: K): Flow<V?> =
      flow {
            var highest = Long.MIN_VALUE
            signal
                .onSubscription {
                  val initial = loadAndCache(key)
                  highest = initial?.version ?: Long.MIN_VALUE
                  this@flow.emit(initial?.value)
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

internal fun <V : Any> VersionedMapEvent<*, V>.valueOrNull(): V? =
    when (this) {
      is VersionedMapEvent.Upsert -> value
      is VersionedMapEvent.Removed -> null
    }
