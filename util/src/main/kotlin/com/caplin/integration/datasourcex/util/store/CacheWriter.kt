package com.caplin.integration.datasourcex.util.store

/**
 * Write half of the store SPI. Implementations enlist on [TxContext.transaction] and must not
 * commit the transaction themselves. Each write assigns and returns the new version from the
 * store's commit order (a sequence, identity, or version column); the caller never supplies it.
 */
interface CacheWriter<K : Any, V : Any, T> {
  /** Writes [value] for [key] and returns the version the store assigned it. */
  fun write(key: K, value: V, tx: TxContext<T>): Long

  /** Writes many entries, returning the version assigned to each. */
  fun writeAll(values: Map<K, V>, tx: TxContext<T>): Map<K, Long> = buildMap {
    for ((key, value) in values) put(key, write(key, value, tx))
  }

  /** Deletes [key] and returns the version the store assigned the removal. */
  fun delete(key: K, tx: TxContext<T>): Long
}
