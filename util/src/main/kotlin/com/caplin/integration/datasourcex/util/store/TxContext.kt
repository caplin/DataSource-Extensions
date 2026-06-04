package com.caplin.integration.datasourcex.util.store

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
