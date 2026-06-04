package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

/**
 * Suspending view of a [FlowStore]: [get] runs the blocking read-through on the store's dispatcher,
 * short-circuiting a cache hit inline so only a miss is dispatched.
 */
interface AsyncFlowStore<K : Any, V : Any> {
  fun asFlow(): SharedFlow<VersionedMapEvent<K, V>>

  fun asFlow(
      query: () -> Map<K, Versioned<V>>,
      predicate: (K, V) -> Boolean = { _, _ -> true },
  ): Flow<VersionedMapEvent<K, V>>

  fun valueFlow(key: K): Flow<V?>

  suspend fun get(key: K): V?
}

/**
 * Suspending view of a [MutableFlowStore]: each call dispatches its blocking work to the store's
 * dispatcher. The mutations take the caller's transaction handle [T], so use this where [T]
 * tolerates use from the dispatcher (a transaction managed across threads with serial access);
 * where the transaction is driven by a thread-bound, non-suspending callback, use the
 * non-suspending [MutableFlowStore] instead.
 */
interface AsyncMutableFlowStore<K : Any, V : Any, T> : AsyncFlowStore<K, V> {
  suspend fun get(key: K, tx: T): V?

  suspend fun put(key: K, value: V, tx: T)

  suspend fun putAll(from: Map<K, V>, tx: T)

  suspend fun remove(key: K, tx: T)
}

internal class AsyncFlowStoreImpl<K : Any, V : Any>(private val store: AbstractFlowStore<K, V>) :
    AsyncFlowStore<K, V> {
  override fun asFlow(): SharedFlow<VersionedMapEvent<K, V>> = store.asFlow()

  override fun asFlow(
      query: () -> Map<K, Versioned<V>>,
      predicate: (K, V) -> Boolean,
  ): Flow<VersionedMapEvent<K, V>> = store.asFlow(query, predicate)

  override fun valueFlow(key: K): Flow<V?> = store.valueFlow(key)

  override suspend fun get(key: K): V? = store.getSuspending(key)
}

internal class AsyncMutableFlowStoreImpl<K : Any, V : Any, T>(
    private val store: MutableFlowStoreImpl<K, V, T>,
    private val dispatcher: CoroutineDispatcher,
) : AsyncMutableFlowStore<K, V, T> {
  override fun asFlow(): SharedFlow<VersionedMapEvent<K, V>> = store.asFlow()

  override fun asFlow(
      query: () -> Map<K, Versioned<V>>,
      predicate: (K, V) -> Boolean,
  ): Flow<VersionedMapEvent<K, V>> = store.asFlow(query, predicate)

  override fun valueFlow(key: K): Flow<V?> = store.valueFlow(key)

  override suspend fun get(key: K): V? = store.getSuspending(key)

  override suspend fun get(key: K, tx: T): V? = withContext(dispatcher) { store.get(key, tx) }

  override suspend fun put(key: K, value: V, tx: T) =
      withContext(dispatcher) { store.put(key, value, tx) }

  override suspend fun putAll(from: Map<K, V>, tx: T) =
      withContext(dispatcher) { store.putAll(from, tx) }

  override suspend fun remove(key: K, tx: T) = withContext(dispatcher) { store.remove(key, tx) }
}
