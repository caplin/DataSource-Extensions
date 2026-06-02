package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A driver-agnostic unit of work. [transaction] is the backend handle (jOOQ, JDBC, R2DBC, ...). The
 * owner of the transaction begins and commits it; callers only register post-commit side-effects
 * via [onCommitEnd] (and optional [onRollback] cleanup).
 */
interface TxContext<out T> {
  val transaction: T

  fun onCommitEnd(action: suspend () -> Unit)

  fun onRollback(action: suspend () -> Unit) {}
}

/**
 * A [TxContext] that buffers registered actions and fires them when [commit] / [rollback] is
 * invoked. Used for the non-transactional, auto-commit write path where the caller drives the
 * single unit of work directly.
 */
class AutoCommitTxContext<out T>(override val transaction: T) : TxContext<T> {
  private val commitActions = CopyOnWriteArrayList<suspend () -> Unit>()
  private val rollbackActions = CopyOnWriteArrayList<suspend () -> Unit>()
  private val completed = AtomicBoolean(false)

  override fun onCommitEnd(action: suspend () -> Unit) {
    commitActions += action
  }

  override fun onRollback(action: suspend () -> Unit) {
    rollbackActions += action
  }

  /** Fires the registered commit actions once; reusing a completed context throws. */
  suspend fun commit() {
    check(completed.compareAndSet(false, true)) { "Transaction already completed" }
    for (action in commitActions) action()
  }

  /**
   * Fires the registered rollback actions; a no-op once the context has committed or rolled back.
   */
  suspend fun rollback() {
    if (!completed.compareAndSet(false, true)) return
    for (action in rollbackActions) action()
  }
}
