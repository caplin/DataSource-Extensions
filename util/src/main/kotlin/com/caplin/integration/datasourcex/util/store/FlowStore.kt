package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

/**
 * Read view of a store-backed map. Exposes the delta stream and per-key access only; there is no
 * full-map snapshot, so values are read through to the store on a miss.
 *
 * The value [V] must be an **aggregate root**: the complete state owned under a key, treated as an
 * opaque whole. Every write replaces a key's value entirely — there are no field-level or partial
 * updates and no merge — each published [VersionedMapEvent.Upsert] carries the full value, and a
 * cache miss reads the whole value back through the store. Together with single-writer-per-key
 * ownership (exactly one process writes a given key) this is what makes a key's version sequence
 * totally ordered.
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
 * Owning, read/write view of a store-backed map. Writes are written through [CacheWriter] and the
 * cache update and delta are applied only when the enclosing transaction commits. [V] must be an
 * aggregate root — see [FlowStore].
 */
interface MutableFlowStore<K : Any, V : Any, T> : FlowStore<K, V> {
  suspend fun put(key: K, value: V)

  suspend fun put(key: K, value: V, tx: TxContext<T>)

  suspend fun putAll(from: Map<K, V>)

  suspend fun putAll(from: Map<K, V>, tx: TxContext<T>)

  suspend fun remove(key: K)

  suspend fun remove(key: K, tx: TxContext<T>)
}

/**
 * Creates a read-only [FlowStore] consumer fed by an [inbound] delta stream (the owner's published
 * mutations) and reading [loader] on a cache miss. The collection of [inbound] is launched in
 * [scope]. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any> flowStore(
    loader: CacheLoader<K, V>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    cache: SuspendingCache<K, Versioned<V>>,
    scope: CoroutineScope,
): FlowStore<K, V> = FlowStoreImpl(loader, cache, inbound, scope)

internal class FlowStoreImpl<K : Any, V : Any>(
    loader: CacheLoader<K, V>,
    cache: SuspendingCache<K, Versioned<V>>,
    inbound: Flow<VersionedMapEvent<K, V>>,
    scope: CoroutineScope,
) : AbstractFlowStore<K, V>(loader, cache) {

  init {
    scope.launch {
      inbound.collect { event ->
        // Keep a resident entry coherent, gating on the version so a delta older than a
        // read-through load is dropped. Non-resident keys are left for the next read-through.
        val cached = cache.asyncCache().getIfPresent(event.key)?.await()
        if (cached != null && event.version > cached.version) {
          when (event) {
            is VersionedMapEvent.Upsert ->
                cache.put(event.key, Versioned(event.value, event.version))
            is VersionedMapEvent.Removed -> cache.invalidate(event.key)
          }
        }
        signal.emit(event)
      }
    }
  }
}
