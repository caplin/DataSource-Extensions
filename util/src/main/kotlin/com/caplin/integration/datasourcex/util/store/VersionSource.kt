package com.caplin.integration.datasourcex.util.store

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Supplies version tokens that are strictly greater than any previously returned. */
fun interface VersionSource {
  fun next(): Long
}

/** A monotonic counter whose first [next] returns `start + 1`. */
class CountingVersionSource(start: Long = 0L) : VersionSource {
  private val counter = AtomicLong(start)

  override fun next(): Long = counter.incrementAndGet()
}

/**
 * Wall-clock micros, guarded with `next = max(prev + 1, now)` so values stay strictly increasing
 * even when the clock stalls within a tick or regresses.
 */
class TimestampVersionSource(
    private val nowMicros: () -> Long = {
      TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
    }
) : VersionSource {
  private val last = AtomicLong(0L)

  override fun next(): Long = last.updateAndGet { prev -> maxOf(prev + 1, nowMicros()) }
}
