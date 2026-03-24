@file:OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

class RetryKtTest :
    FunSpec({
      test("Retry with exponential backoff") {
        val startCount = AtomicInteger()
        val channel = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        channel
            .receiveAsFlow()
            .dematerialize()
            .onStart { println("Starting ${startCount.getAndIncrement()}") }
            .retryWithExponentialBackoff(minMillis = 100, maxMillis = 500) { _, delay ->
              println("Retrying in $delay")
              true
            }
            .flowOn(currentCoroutineContext()[CoroutineDispatcher]!!)
            .test {
              delay(1)
              startCount.get() shouldBeEqual 1
              channel.send(Value("A"))
              awaitItem() shouldBeEqual "A"

              channel.send(Completion(IllegalArgumentException()))
              expectNoEvents()

              delay(101)
              startCount.get() shouldBeEqual 2

              channel.send(Completion(IllegalArgumentException()))

              delay(199)
              expectNoEvents()
              startCount.get() shouldBeEqual 2

              delay(1)
              startCount.get() shouldBeEqual 3

              channel.send(Completion(IllegalArgumentException()))

              delay(399)
              expectNoEvents()
              startCount.get() shouldBeEqual 3

              delay(1)
              startCount.get() shouldBeEqual 4

              channel.send(Completion(IllegalArgumentException()))

              delay(499) // We've hit our ceiling
              expectNoEvents()
              startCount.get() shouldBeEqual 4

              delay(1)
              startCount.get() shouldBeEqual 5

              channel.send(
                  Value("B")
              ) // Emission of a valid value resets our delay to the initial value
              awaitItem() shouldBeEqual "B"

              channel.send(Completion(IllegalArgumentException()))

              delay(99) // Reset to the initial delay
              expectNoEvents()
              startCount.get() shouldBeEqual 5

              delay(1)
              startCount.get() shouldBeEqual 6
            }
      }

      test("Retry stops when onRetry returns false") {
        val startCount = AtomicInteger()
        val channel = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        channel
            .receiveAsFlow()
            .dematerialize()
            .onStart { startCount.getAndIncrement() }
            .retryWithExponentialBackoff(minMillis = 10, maxMillis = 100) { _, _ ->
              startCount.get() < 3
            }
            .test {
              channel.send(Completion(RuntimeException("fail")))
              // 1st failure: onRetry(1 < 3) is true. Retries.
              delay(15)
              startCount.get() shouldBeEqual 2

              channel.send(Completion(RuntimeException("fail")))
              // 2nd failure: onRetry(2 < 3) is true. Retries.
              delay(35)
              startCount.get() shouldBeEqual 3

              channel.send(Completion(RuntimeException("fail")))
              // 3rd failure: onRetry(3 < 3) is false. Stops.

              awaitError().message shouldBe "fail"
              startCount.get() shouldBeEqual 3
            }
      }
    })
