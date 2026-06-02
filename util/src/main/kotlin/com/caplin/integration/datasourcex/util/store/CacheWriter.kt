package com.caplin.integration.datasourcex.util.store

/**
 * Write half of the store SPI. Implementations enlist on [TxContext.transaction] and must not
 * commit the transaction themselves.
 */
interface CacheWriter<K : Any, V : Any, T> {
  suspend fun write(key: K, value: V, version: Long, tx: TxContext<T>)

  suspend fun writeAll(entries: Map<K, Versioned<V>>, tx: TxContext<T>) {
    for ((key, versioned) in entries) write(key, versioned.value, versioned.version, tx)
  }

  suspend fun delete(key: K, version: Long, tx: TxContext<T>)

  suspend fun deleteAll(keys: Collection<K>, version: Long, tx: TxContext<T>) {
    for (key in keys) delete(key, version, tx)
  }
}
