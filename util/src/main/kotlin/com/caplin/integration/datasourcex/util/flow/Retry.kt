package com.caplin.integration.datasourcex.util.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry

/**
 * On an error, Retry on the upstream with an exponential delay between each attempt. Behaviour can
 * be modified by proving [onRetry] - if a call to this returns `false` it will stop retrying and
 * propagate the error downstream.
 */
fun <T : Any?> Flow<T>.retryWithExponentialBackoff(
    minMillis: Long = 100L,
    maxMillis: Long = 60000L,
    onRetry: (suspend (Throwable, Long) -> Boolean) = { _, _ -> true },
): Flow<T> {
  check(minMillis < maxMillis) { "Min millis $minMillis should be less than max millis $maxMillis" }
  return flow {
    var nextDelay = minMillis
    emitAll(
        retry {
              if (onRetry(it, nextDelay)) {
                delay(nextDelay)
                nextDelay = minOf(maxMillis, nextDelay * 2)
                true
              } else false
            }
            .onEach { nextDelay = minMillis }
    )
  }
}
