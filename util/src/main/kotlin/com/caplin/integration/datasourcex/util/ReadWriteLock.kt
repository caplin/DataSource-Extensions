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

  suspend fun <R> withWriteLock(block: suspend () -> R): R = writerMutex.withLock(null) { block() }

  suspend fun <R> withReadLock(block: suspend () -> R): R =
      withContext(NonCancellable) {
        readLock()
        try {
          block()
        } finally {
          readUnlock()
        }
      }

  suspend fun readLock() =
      withContext(NonCancellable) {
        readerMutex.withLock {
          readerCount++
          if (readerCount == 1) writeLock()
        }
      }

  suspend fun readUnlock() =
      withContext(NonCancellable) {
        readerMutex.withLock {
          readerCount--
          if (readerCount == 0) writeUnlock()
        }
      }

  suspend fun writeLock() = writerMutex.lock()

  fun writeUnlock() = writerMutex.unlock()
}
