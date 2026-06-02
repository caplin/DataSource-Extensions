package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent

/**
 * Creates a [MutableFlowStore] backed by [loader] / [writer] and the bounded hot set [cache]. The
 * [writer] assigns each write's version. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    loader: CacheLoader<K, V>,
    writer: CacheWriter<K, V, T>,
    transactionRunner: TransactionRunner<T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
): MutableFlowStore<K, V, T> =
    MutableFlowStoreImpl(loader, writer, transactionRunner, cache, bufferCapacity)

/**
 * Creates a [MutableFlowStore] backed by a combined [CacheLoaderWriter] and the bounded hot set
 * [cache]. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    store: CacheLoaderWriter<K, V, T>,
    transactionRunner: TransactionRunner<T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
): MutableFlowStore<K, V, T> =
    mutableFlowStore(store, store, transactionRunner, cache, bufferCapacity)

internal class MutableFlowStoreImpl<K : Any, V : Any, T>(
    loader: CacheLoader<K, V>,
    private val writer: CacheWriter<K, V, T>,
    private val transactionRunner: TransactionRunner<T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    bufferCapacity: Int = DEFAULT_SIGNAL_BUFFER,
) : AbstractFlowStore<K, V>(loader, cache, bufferCapacity), MutableFlowStore<K, V, T> {

  override suspend fun put(key: K, value: V) = transactionRunner.run { put(key, value, it) }

  override suspend fun put(key: K, value: V, tx: TxContext<T>) {
    val version = writer.write(key, value, tx)
    tx.onCommitEnd {
      // Publish the delta first, then refresh the local cache: a cancellation between the two would
      // otherwise leave the owner cache ahead of a delta no consumer ever sees. The cache write is
      // version-gated so an interleaved post-commit reflection cannot regress it.
      signal.emit(VersionedMapEvent.Upsert(key, value, version))
      cachePutIfNewer(key, Live(value, version))
    }
  }

  override suspend fun putAll(from: Map<K, V>) = transactionRunner.run { putAll(from, it) }

  override suspend fun putAll(from: Map<K, V>, tx: TxContext<T>) {
    val versions = writer.writeAll(from, tx)
    tx.onCommitEnd {
      versions.forEach { (key, version) ->
        val value = from.getValue(key)
        signal.emit(VersionedMapEvent.Upsert(key, value, version))
        cachePutIfNewer(key, Live(value, version))
      }
    }
  }

  override suspend fun remove(key: K) = transactionRunner.run { remove(key, it) }

  override suspend fun remove(key: K, tx: TxContext<T>) {
    val version = writer.delete(key, tx)
    tx.onCommitEnd {
      signal.emit(VersionedMapEvent.Removed(key, version))
      cachePutIfNewer(key, Tombstone(version))
    }
  }
}
