package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * In-memory reference implementation of the store SPI for tests. Writes apply immediately to a
 * backing [ConcurrentHashMap], versioned from a shared counter that stands in for a DB sequence;
 * the [TxContext] (type [Unit]) is ignored.
 */
class InMemoryCacheLoaderWriter<K : Any, V : Any> : CacheLoaderWriter<K, V, Unit> {
  private val backing = ConcurrentHashMap<K, Versioned<V>>()
  private val sequence = AtomicLong(0L)

  /** Seeds a specific version directly, for tests that exercise version gating. */
  fun seed(key: K, value: V, version: Long) {
    backing[key] = Versioned(value, version)
    sequence.updateAndGet { maxOf(it, version) }
  }

  override suspend fun load(key: K): Versioned<V>? = backing[key]

  override fun loadAllKeys(): Flow<K> = backing.keys.toList().asFlow()

  override suspend fun write(key: K, value: V, tx: TxContext<Unit>): Long {
    val version = sequence.incrementAndGet()
    backing[key] = Versioned(value, version)
    return version
  }

  override suspend fun delete(key: K, tx: TxContext<Unit>): Long {
    val version = sequence.incrementAndGet()
    backing.remove(key)
    return version
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
