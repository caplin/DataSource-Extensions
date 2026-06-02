package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A driver-agnostic unit of work. [transaction] is the backend handle (jOOQ, JDBC, R2DBC, ...). The
 * owner of the transaction begins and commits it; callers only register post-commit side-effects
 * via [onCommitEnd] (and optional [onRollback] cleanup).
 *
 * Registered actions are **non-suspending** so they can run inside a synchronous commit/rollback
 * callback (e.g. a jOOQ `TransactionListener`). The store's actions are cheap — a version-gated
 * cache update and a `tryEmit` of the delta.
 */
interface TxContext<out T> {
  val transaction: T

  fun onCommitEnd(action: () -> Unit)

  fun onRollback(action: () -> Unit) {}
}

/**
 * A [TxContext] that buffers registered actions and fires them when [commit] / [rollback] is
 * invoked. Used for the non-transactional, auto-commit write path where the caller drives the
 * single unit of work directly.
 */
class AutoCommitTxContext<out T>(override val transaction: T) : TxContext<T> {
  private val commitActions = CopyOnWriteArrayList<() -> Unit>()
  private val rollbackActions = CopyOnWriteArrayList<() -> Unit>()
  private val completed = AtomicBoolean(false)

  override fun onCommitEnd(action: () -> Unit) {
    commitActions += action
  }

  override fun onRollback(action: () -> Unit) {
    rollbackActions += action
  }

  /** Fires the registered commit actions once; reusing a completed context throws. */
  fun commit() {
    check(completed.compareAndSet(false, true)) { "Transaction already completed" }
    for (action in commitActions) action()
  }

  /**
   * Fires the registered rollback actions; a no-op once the context has committed or rolled back.
   */
  fun rollback() {
    if (!completed.compareAndSet(false, true)) return
    for (action in rollbackActions) action()
  }
}
