package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
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
 * Operator form of [flowStore], reading this delta stream as the `inbound` source: `deltas
 * .flowStoreIn(reader, caffeine, scope)`. Mirrors `shareIn`/`stateIn` — collection is launched in
 * [scope] and the returned [FlowStore] is the read-only consumer. See [flowStore] for the
 * lifecycle, caching, and [dispatcher] semantics.
 */
fun <K : Any, V : Any> Flow<VersionedMapEvent<K, V>>.flowStoreIn(
    reader: StoreReader<K, V>,
    caffeine: Caffeine<Any, Any>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
): FlowStore<K, V> =
    flowStore(
        reader,
        this,
        caffeine,
        scope,
        dispatcher,
        bufferCapacity,
    )

/**
 * Creates a read-only [FlowStore] consumer fed by an [inbound] delta stream (the owner's published
 * mutations) and reading [reader] on a cache miss. The collection of [inbound] is launched in
 * [scope], so cancelling [scope] stops it and a failure of [inbound] surfaces through [scope] (its
 * parent / exception handler), after which the store serves only stale reads. The hot set is a
 * [Caffeine] cache built from [caffeine] (size it to bound memory). The blocking read-through load
 * runs on [dispatcher] (IO by default). [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any> flowStore(
    reader: StoreReader<K, V>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    caffeine: Caffeine<Any, Any>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
): FlowStore<K, V> =
    FlowStoreImpl(
        reader,
        caffeine.buildFlowStoreCache(),
        inbound,
        scope,
        dispatcher,
        bufferCapacity,
    )

internal class FlowStoreImpl<K : Any, V : Any>(
    reader: StoreReader<K, V>,
    cache: FlowStoreCache<K, V>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : AbstractFlowStore<K, V>(reader, cache, dispatcher, bufferCapacity) {

  init {
    scope.launch {
      inbound.collect { event ->
        cache.reflectIfNewer(event)
        signal.emit(event)
      }
    }
  }
}
