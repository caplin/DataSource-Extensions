@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * If the upstream emits no first event within [millis] milliseconds it will emit the event returned
 * by [default] followed by all later emissions from the upstream.
 */
@Suppress("UNCHECKED_CAST")
fun <T, R> Flow<T>.timeoutFirstOrDefault(millis: Long, default: () -> R): Flow<R> = channelFlow {
  val receiveChannel = produce { collect { send(it) } }

  select {
    receiveChannel.onReceive { result -> send(result as R) }
    onTimeout(millis) { send(default()) }
  }
  receiveChannel.consumeEach { send(it as R) }
}

/**
 * If the upstream emits no first event within [duration] it will emit the event returned by
 * [default] followed by all later emissions from the upstream.
 */
fun <T, R> Flow<T>.timeoutFirstOrDefault(duration: Duration, default: () -> R): Flow<R> =
    timeoutFirstOrDefault(duration.toMillis(), default)

/**
 * If the upstream emits no first event within [millis] milliseconds it will emit the event
 * [default] followed by all later emissions from the upstream.
 */
fun <T, R> Flow<T>.timeoutFirstOrDefault(millis: Long, default: R): Flow<R> =
    timeoutFirstOrDefault(millis) { default }

/**
 * If the upstream emits no first event within [duration] it will emit the event [default] followed
 * by all later emissions from the upstream.
 */
fun <T, R : Any?> Flow<T>.timeoutFirstOrDefault(duration: Duration, default: R): Flow<R> =
    timeoutFirstOrDefault(duration.toMillis(), default)

/**
 * If the upstream emits no first event within [millis] milliseconds it will emit `null` followed by
 * all later emissions from the upstream.
 */
fun <T> Flow<T>.timeoutFirstOrNull(millis: Long): Flow<T?> = timeoutFirstOrDefault(millis) { null }

/**
 * If the upstream emits no first event within [duration] it will emit `null` followed by all later
 * emissions from the upstream.
 */
fun <T> Flow<T>.timeoutFirstOrNull(duration: Duration): Flow<T?> =
    timeoutFirstOrNull(duration.toMillis())

/**
 * If the upstream emits no first event within [millis] milliseconds it will emit an
 * [TimeoutException] error.
 */
fun <T> Flow<T>.timeoutFirst(millis: Long): Flow<T> =
    timeoutFirstOrDefault(millis) { throw TimeoutException("No event received in ${millis}ms") }

/**
 * If the upstream emits no first event within [duration] it will emit an [TimeoutException] error.
 */
fun <T> Flow<T>.timeoutFirst(duration: Duration): Flow<T> = timeoutFirst(duration.toMillis())
