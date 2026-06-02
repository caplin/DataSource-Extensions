package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Read view of a store-backed map. Exposes the delta stream and per-key access only; there is no
 * full-map snapshot, so values are read through to the store on a miss.
 *
 * The value [V] must be an **aggregate root**: the complete state owned under a key, treated as an
 * opaque whole. Every write replaces a key's value entirely — there are no field-level or partial
 * updates and no merge — each published [VersionedMapEvent.Upsert] carries the full value, and a
 * cache miss reads the whole value back through the store. Together with single-writer-per-key
 * ownership (exactly one process writes a given key, and it serialises writes to that key) this is
 * what makes a key's version sequence totally ordered.
 *
 * It is therefore unsuitable for values updated field-by-field by multiple writers, partial
 * projections that must be merged, or values large enough that republishing the whole value on
 * every change is too costly.
 */
interface FlowStore<K : Any, V : Any> {
  /** The live, delta-only stream of versioned mutations. */
  fun asFlow(): Flow<VersionedMapEvent<K, V>>

  /** The latest value for [key], starting from a read-through load then following the stream. */
  fun valueFlow(key: K): Flow<V?>

  suspend fun get(key: K): V?
}

/**
 * Read/write view of a store-backed map. Every mutation takes the caller's transaction handle [T]
 * (a jOOQ `Configuration`, a JDBC `Connection`, …): the write is enlisted on it through
 * [CacheWriter], which assigns the version, and the cache update and delta are published only when
 * that transaction commits.
 *
 * The owner must **serialise writes to a given key** (single-writer-per-key): the version is the
 * store's commit order, so concurrent unserialised writes to the same key would let the persisted
 * row, not just the cache, settle on the wrong version. [V] must be an aggregate root — see
 * [FlowStore].
 */
interface MutableFlowStore<K : Any, V : Any, T> : FlowStore<K, V> {
  /**
   * Reads [key]'s current value within [tx], always through the store and bypassing the cache, so
   * it sees this transaction's own uncommitted writes and can take a locking read. Use for
   * read-modify-write; use the cache-first [get] outside a transaction.
   */
  fun get(key: K, tx: T): V?

  fun put(key: K, value: V, tx: T)

  fun putAll(from: Map<K, V>, tx: T)

  fun remove(key: K, tx: T)
}

/**
 * Creates a read-only [FlowStore] consumer fed by an [inbound] delta stream (the owner's published
 * mutations) and reading [loader] on a cache miss. The collection of [inbound] is launched in
 * [scope]. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any> flowStore(
    loader: CacheLoader<K, V>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    scope: CoroutineScope,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
): FlowStore<K, V> = FlowStoreImpl(loader, cache, inbound, scope, bufferCapacity)

internal class FlowStoreImpl<K : Any, V : Any>(
    loader: CacheLoader<K, V>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    scope: CoroutineScope,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : AbstractFlowStore<K, V>(loader, cache, bufferCapacity) {

  init {
    scope.launch {
      inbound.collect { event ->
        cacheReflectIfNewer(event)
        signal.emit(event)
      }
    }
  }
}
