package com.caplin.integration.datasourcex.util.store

/** Opens a unit of work, runs [block], then commits on success or rolls back on failure. */
fun interface TransactionRunner<T> {
  suspend fun run(block: suspend (TxContext<T>) -> Unit)
}
