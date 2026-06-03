package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A driver-agnostic unit of work. [transaction] is the underlying transaction handle. The owner of
 * the transaction begins and commits it; callers only register post-commit side-effects via
 * [onCommitEnd] (and optional [onRollback] cleanup), which run inside a synchronous commit/rollback
 * callback.
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
    runAll(commitActions)
  }

  /**
   * Fires the registered rollback actions; a no-op once the context has committed or rolled back.
   */
  fun rollback() {
    if (!completed.compareAndSet(false, true)) return
    runAll(rollbackActions)
  }

  // Run every action even if some throw, so one failure cannot strand the rest; the first failure
  // propagates with the others suppressed.
  private fun runAll(actions: List<() -> Unit>) {
    var failure: Throwable? = null
    for (action in actions) {
      try {
        action()
      } catch (t: Throwable) {
        if (failure == null) failure = t else failure.addSuppressed(t)
      }
    }
    failure?.let { throw it }
  }
}
