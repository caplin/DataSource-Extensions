package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A trivial in-memory unit of work standing in for a database transaction: it buffers the store's
 * post-commit / rollback side effects until the test drives [commit] or [rollback]. Mirrors how a
 * jOOQ `Configuration` carries transaction-scoped state.
 */
class InMemoryTx {
  internal val commitActions = mutableListOf<() -> Unit>()
  internal val rollbackActions = mutableListOf<() -> Unit>()

  fun commit() = commitActions.forEach { it() }

  fun rollback() = rollbackActions.forEach { it() }
}

/** Adapts an [InMemoryTx] handle into the [TxContext] the store enlists on. */
val inMemoryTxContext: (InMemoryTx) -> TxContext<InMemoryTx> = { handle ->
  object : TxContext<InMemoryTx> {
    override val transaction = handle

    override fun onCommitEnd(action: () -> Unit) {
      handle.commitActions += action
    }

    override fun onRollback(action: () -> Unit) {
      handle.rollbackActions += action
    }
  }
}

/**
 * In-memory reference implementation of the store SPI for tests. Writes apply immediately to a
 * backing [ConcurrentHashMap], versioned from a shared counter that stands in for a DB sequence;
 * the [TxContext] is ignored.
 */
class InMemoryStore<K : Any, V : Any> : Store<K, V, InMemoryTx> {
  private val backing = ConcurrentHashMap<K, Versioned<V>>()
  private val sequence = AtomicLong(0L)
  val loadCount = AtomicInteger(0)

  /** Seeds a specific version directly, for tests that exercise version gating. */
  fun seed(key: K, value: V, version: Long) {
    backing[key] = Versioned(value, version)
    sequence.updateAndGet { maxOf(it, version) }
  }

  override fun load(key: K): Versioned<V>? {
    loadCount.incrementAndGet()
    return backing[key]
  }

  override fun load(key: K, tx: TxContext<InMemoryTx>): Versioned<V>? = backing[key]

  override fun write(key: K, value: V, tx: TxContext<InMemoryTx>): Long {
    val version = sequence.incrementAndGet()
    backing[key] = Versioned(value, version)
    return version
  }

  override fun delete(key: K, tx: TxContext<InMemoryTx>): Long {
    val version = sequence.incrementAndGet()
    backing.remove(key)
    return version
  }
}
