package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Read view of a store-backed map. Exposes the delta stream and per-key access only; there is no
 * full-map snapshot, so values are read through to the store on a miss.
 *
 * The value [V] must be an **aggregate root**: every write replaces a key's value as a whole (no
 * field-level or partial updates), so each [VersionedMapEvent.Upsert] carries the full value and a
 * miss reads the whole value back. With single-writer-per-key ownership this makes a key's version
 * sequence totally ordered.
 */
interface FlowStore<K : Any, V : Any> {
  /** The live, delta-only stream of versioned mutations. */
  fun asFlow(): SharedFlow<VersionedMapEvent<K, V>>

  /** The latest value for [key], starting from a read-through load then following the stream. */
  fun valueFlow(key: K): Flow<V?>

  /**
   * A suspending view of this store whose [AsyncFlowStore.get] dispatches the read-through itself.
   */
  val async: AsyncFlowStore<K, V>

  /**
   * The latest value for [key]: cache-first, reading through to the store on a miss. The
   * read-through is blocking — call it within an IO context, as with the store's writes. Not
   * ordered against [asFlow]: a value just published as a delta may not be visible here yet.
   */
  fun get(key: K): V?
}

/**
 * Creates a read-only [FlowStore] consumer fed by an [inbound] delta stream (the owner's published
 * mutations) and reading [loader] on a cache miss. The collection of [inbound] is launched in
 * [scope]. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any> flowStore(
    loader: CacheLoader<K, V>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    cache: Cache<K, CacheEntry<V>?>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
): FlowStore<K, V> =
    FlowStoreImpl(loader, FlowStoreCache(cache), inbound, scope, dispatcher, bufferCapacity)

internal class FlowStoreImpl<K : Any, V : Any>(
    loader: CacheLoader<K, V>,
    cache: FlowStoreCache<K, V>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : AbstractFlowStore<K, V>(loader, cache, dispatcher, bufferCapacity) {

  init {
    scope.launch {
      inbound.collect { event ->
        cache.reflectIfNewer(event)
        signal.emit(event)
      }
    }
  }
}
