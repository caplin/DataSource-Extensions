package com.caplin.integration.datasourcex.util

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * Use this instead of [kotlinx.coroutines.withTimeout] as that throws
 * [TimeoutCancellationException] which extends [CancellationException] and can result in
 * [unexpected behaviour](https://github.com/Kotlin/kotlinx.coroutines/pull/4356)
 */
suspend fun <T> withTimeout(
    duration: java.time.Duration,
    block: suspend CoroutineScope.() -> T
): T = withTimeout(duration.toMillis(), block)

/**
 * Use this instead of [kotlinx.coroutines.withTimeout] as that throws
 * [TimeoutCancellationException] which extends [CancellationException] and can result in
 * [unexpected behaviour](https://github.com/Kotlin/kotlinx.coroutines/pull/4356)
 */
suspend fun <T> withTimeout(
    duration: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> T
): T = withTimeout(duration.inWholeMilliseconds, block)

/**
 * Use this instead of [kotlinx.coroutines.withTimeout] as that throws
 * [TimeoutCancellationException] which extends [CancellationException] and can result in
 * [unexpected behaviour](https://github.com/Kotlin/kotlinx.coroutines/pull/4356)
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> withTimeout(millis: Long, block: suspend CoroutineScope.() -> T): T =
    coroutineScope {
      select {
        async { block() }.onAwait { it }
        onTimeout(millis) { throw TimeoutException("Timed out waiting for $millis ms") }
      }
    }
