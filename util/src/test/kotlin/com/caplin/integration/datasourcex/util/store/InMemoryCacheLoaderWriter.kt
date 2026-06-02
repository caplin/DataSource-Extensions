package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * In-memory reference implementation of the store SPI for tests. Writes apply immediately to a
 * backing [ConcurrentHashMap]; the [TxContext] (type [Unit]) is ignored.
 */
class InMemoryCacheLoaderWriter<K : Any, V : Any> : CacheLoaderWriter<K, V, Unit> {
  private val backing = ConcurrentHashMap<K, Versioned<V>>()

  override suspend fun load(key: K): Versioned<V>? = backing[key]

  override fun loadAllKeys(): Flow<K> = backing.keys.toList().asFlow()

  override suspend fun write(key: K, value: V, version: Long, tx: TxContext<Unit>) {
    backing[key] = Versioned(value, version)
  }

  override suspend fun delete(key: K, version: Long, tx: TxContext<Unit>) {
    backing.remove(key)
  }
}

/**
 * A [TransactionRunner] over a [Unit] transaction that commits on success and rolls back on error.
 */
val inMemoryTransactionRunner = TransactionRunner { block ->
  val tx = AutoCommitTxContext(Unit)
  try {
    block(tx)
    tx.commit()
  } catch (e: Throwable) {
    tx.rollback()
    throw e
  }
}
