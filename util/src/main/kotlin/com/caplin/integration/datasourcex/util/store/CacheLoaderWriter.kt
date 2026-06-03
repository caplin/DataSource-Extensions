package com.caplin.integration.datasourcex.util.store

/** Combines [CacheLoader] and [CacheWriter] for a backend that implements both. */
interface CacheLoaderWriter<K : Any, V : Any, T> : CacheLoader<K, V>, CacheWriter<K, V, T> {
  /**
   * Reads [key]'s current persisted value within [tx] for read-modify-write, e.g. a locking `SELECT
   * … FOR UPDATE` on the transaction's connection. Override it to support [MutableFlowStore.get]
   * within a transaction (the default throws); the read should see the transaction's own
   * uncommitted writes and serialise concurrent writers.
   */
  fun load(key: K, tx: TxContext<T>): Versioned<V>? =
      throw UnsupportedOperationException(
          "Override load(key, tx) to support get(key, tx) / read-modify-write",
      )
}
