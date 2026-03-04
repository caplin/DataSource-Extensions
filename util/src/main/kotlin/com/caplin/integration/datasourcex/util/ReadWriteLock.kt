package com.caplin.integration.datasourcex.util

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A non-reentrant suspending implementation of a Read-Write lock. */
class ReadWriteLock {
  private var readerCount = 0
  private val readerMutex = Mutex()
  private val writerMutex = Mutex()

  /**
   * Executes the given [block] of code within a write lock. Suspends until the write lock can be
   * acquired.
   */
  suspend fun <R> withWriteLock(block: suspend () -> R): R = writerMutex.withLock(null) { block() }

  /**
   * Executes the given [block] of code within a read lock. Suspends until the read lock can be
   * acquired.
   */
  suspend fun <R> withReadLock(block: suspend () -> R): R =
      withContext(NonCancellable) {
        readLock()
        try {
          block()
        } finally {
          readUnlock()
        }
      }

  /** Acquires a read lock. Suspends if a write lock is currently held. */
  suspend fun readLock() =
      withContext(NonCancellable) {
        readerMutex.withLock {
          readerCount++
          if (readerCount == 1) writeLock()
        }
      }

  /** Releases a previously acquired read lock. */
  suspend fun readUnlock() =
      withContext(NonCancellable) {
        readerMutex.withLock {
          readerCount--
          if (readerCount == 0) writeUnlock()
        }
      }

  /** Acquires a write lock. Suspends if any read or write locks are currently held. */
  suspend fun writeLock() = writerMutex.lock()

  /** Releases a previously acquired write lock. */
  fun writeUnlock() = writerMutex.unlock()
}
