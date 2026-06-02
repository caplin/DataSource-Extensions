package com.caplin.integration.datasourcex.util.store

/**
 * Write half of the store SPI. Implementations enlist on [TxContext.transaction] and must not
 * commit the transaction themselves. Each write assigns and returns the new version: the version is
 * the store's commit order (a sequence, identity, or version column), never supplied by the caller,
 * so it survives restarts and orders writes the way the store committed them.
 */
interface CacheWriter<K : Any, V : Any, T> {
  /** Writes [value] for [key] and returns the version the store assigned it. */
  suspend fun write(key: K, value: V, tx: TxContext<T>): Long

  /** Writes many entries, returning the version assigned to each. */
  suspend fun writeAll(values: Map<K, V>, tx: TxContext<T>): Map<K, Long> = buildMap {
    for ((key, value) in values) put(key, write(key, value, tx))
  }

  /** Deletes [key] and returns the version the store assigned the removal. */
  suspend fun delete(key: K, tx: TxContext<T>): Long

  /** Deletes many keys, returning the version assigned to each removal. */
  suspend fun deleteAll(keys: Collection<K>, tx: TxContext<T>): Map<K, Long> = buildMap {
    for (key in keys) put(key, delete(key, tx))
  }
}
