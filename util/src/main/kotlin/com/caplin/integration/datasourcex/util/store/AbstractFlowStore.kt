package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription

internal const val DEFAULT_SIGNAL_BUFFER: Int = 256

/**
 * Shared core for the store-backed [FlowStore] variants: a delta-only [signal] bus and a versioned
 * hot-set [cache] read through to the store on a miss.
 */
internal abstract class AbstractFlowStore<K : Any, V : Any>(
    protected val loader: CacheLoader<K, V>,
    protected val cache: FlowStoreCache<K, V>,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : FlowStore<K, V> {

  protected val signal =
      MutableSharedFlow<VersionedMapEvent<K, V>>(
          replay = 0,
          extraBufferCapacity = bufferCapacity,
          onBufferOverflow = BufferOverflow.SUSPEND,
      )

  override fun asFlow(): Flow<VersionedMapEvent<K, V>> = signal.asSharedFlow()

  override suspend fun get(key: K): V? = cache.loadIfNewer(key, loader::load)?.valueOrNull()

  override fun valueFlow(key: K): Flow<V?> =
      flow {
            var highest = Long.MIN_VALUE
            signal
                .onSubscription {
                  val initial = cache.loadIfNewer(key, loader::load)
                  highest = initial?.version ?: Long.MIN_VALUE
                  this@flow.emit(initial?.valueOrNull())
                }
                .collect { event ->
                  if (event.key == key && event.version > highest) {
                    highest = event.version
                    this@flow.emit(event.toEntry().valueOrNull())
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

internal fun <V : Any> VersionedMapEvent<*, V>.toEntry(): CacheEntry<V> =
    when (this) {
      is VersionedMapEvent.Upsert -> Live(value, version)
      is VersionedMapEvent.Removed -> Tombstone(version)
    }
