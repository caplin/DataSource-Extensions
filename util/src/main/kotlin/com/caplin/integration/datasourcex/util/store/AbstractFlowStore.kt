package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext

internal const val DEFAULT_SIGNAL_BUFFER: Int = 256

/**
 * Shared core for the store-backed [FlowStore] variants: a delta-only [signal] bus and a versioned
 * hot-set [cache] read through to the store on a miss. The blocking read-through load runs on
 * [dispatcher].
 */
internal abstract class AbstractFlowStore<K : Any, V : Any>(
    protected val loader: StoreReader<K, V>,
    protected val cache: FlowStoreCache<K, V>,
    protected val dispatcher: CoroutineDispatcher,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : FlowStore<K, V> {

  protected val signal =
      MutableSharedFlow<VersionedMapEvent<K, V>>(
          replay = 0,
          extraBufferCapacity = bufferCapacity,
          onBufferOverflow = BufferOverflow.SUSPEND,
      )

  override fun asFlow(): SharedFlow<VersionedMapEvent<K, V>> = signal.asSharedFlow()

  override val async: AsyncFlowStore<K, V> by lazy { AsyncFlowStoreImpl(this) }

  // Blocking on a miss; the caller dispatches it, as with the transactional operations.
  override fun get(key: K): V? = readThrough(key)?.valueOrNull()

  private fun readThrough(key: K): CacheEntry<V>? = cache.getOrLoad(key, loader::load)

  // Suspending [get] for the [async] view: a cache hit returns inline; only a miss is dispatched.
  internal suspend fun getSuspending(key: K): V? {
    cache.getIfPresent(key)?.let {
      return it.valueOrNull()
    }
    return withContext(dispatcher) { cache.getOrLoad(key, loader::load) }?.valueOrNull()
  }

  override fun asFlow(
      query: () -> Map<K, Versioned<V>>,
      predicate: (K, V) -> Boolean,
  ): Flow<VersionedMapEvent<K, V>> = flow {
    val highest = HashMap<K, Long>() // keys in view -> last version emitted
    signal
        .onSubscription {
          val snapshot = withContext(dispatcher) { query() }
          snapshot.forEach { (key, v) ->
            highest[key] = v.version
            this@flow.emit(VersionedMapEvent.Upsert(key, v.value, v.version))
          }
        }
        .collect { event ->
          val baseline = highest[event.key]
          if (baseline != null && event.version <= baseline) return@collect
          when (event) {
            is VersionedMapEvent.Upsert ->
                if (predicate(event.key, event.value)) {
                  highest[event.key] = event.version
                  this@flow.emit(event)
                } else if (baseline != null) {
                  highest.remove(event.key)
                  this@flow.emit(VersionedMapEvent.Removed(event.key, event.version))
                }
            is VersionedMapEvent.Removed ->
                if (baseline != null) {
                  highest.remove(event.key)
                  this@flow.emit(event)
                }
          }
        }
  }

  override fun valueFlow(key: K): Flow<V?> =
      flow {
            var highest = Long.MIN_VALUE
            signal
                .onSubscription {
                  val initial = withContext(dispatcher) { readThrough(key) }
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
