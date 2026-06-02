package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent

/**
 * Creates a [MutableFlowStore] backed by [loader] / [writer] and the bounded hot set [cache]. [V]
 * must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    loader: CacheLoader<K, V>,
    writer: CacheWriter<K, V, T>,
    transactionRunner: TransactionRunner<T>,
    cache: SuspendingCache<K, Versioned<V>>,
    versionSource: VersionSource = CountingVersionSource(),
): MutableFlowStore<K, V, T> =
    MutableFlowStoreImpl(loader, writer, transactionRunner, cache, versionSource)

/**
 * Creates a [MutableFlowStore] backed by a combined [CacheLoaderWriter] and the bounded hot set
 * [cache]. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    store: CacheLoaderWriter<K, V, T>,
    transactionRunner: TransactionRunner<T>,
    cache: SuspendingCache<K, Versioned<V>>,
    versionSource: VersionSource = CountingVersionSource(),
): MutableFlowStore<K, V, T> =
    MutableFlowStoreImpl(store, store, transactionRunner, cache, versionSource)

internal class MutableFlowStoreImpl<K : Any, V : Any, T>(
    loader: CacheLoader<K, V>,
    private val writer: CacheWriter<K, V, T>,
    private val transactionRunner: TransactionRunner<T>,
    cache: SuspendingCache<K, Versioned<V>>,
    private val versionSource: VersionSource,
) : AbstractFlowStore<K, V>(loader, cache), MutableFlowStore<K, V, T> {

  override suspend fun put(key: K, value: V) = transactionRunner.run { put(key, value, it) }

  override suspend fun put(key: K, value: V, tx: TxContext<T>) {
    val version = versionSource.next()
    writer.write(key, value, version, tx)
    tx.onCommitEnd {
      cache.put(key, Versioned(value, version))
      signal.emit(VersionedMapEvent.Upsert(key, value, version))
    }
  }

  override suspend fun putAll(from: Map<K, V>) = transactionRunner.run { putAll(from, it) }

  override suspend fun putAll(from: Map<K, V>, tx: TxContext<T>) {
    val versioned = from.mapValues { (_, value) -> Versioned(value, versionSource.next()) }
    writer.writeAll(versioned, tx)
    tx.onCommitEnd {
      versioned.forEach { (k, v) ->
        cache.put(k, v)
        signal.emit(VersionedMapEvent.Upsert(k, v.value, v.version))
      }
    }
  }

  override suspend fun remove(key: K) = transactionRunner.run { remove(key, it) }

  override suspend fun remove(key: K, tx: TxContext<T>) {
    val version = versionSource.next()
    writer.delete(key, version, tx)
    tx.onCommitEnd {
      cache.invalidate(key)
      signal.emit(VersionedMapEvent.Removed(key, version))
    }
  }
}
