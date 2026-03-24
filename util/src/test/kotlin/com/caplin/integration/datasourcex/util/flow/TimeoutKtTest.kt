@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow

internal class TimeoutKtTest :
    FunSpec({
      test("Timeout first - Triggers") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirst(10).test {
          delay(9)
          expectNoEvents()
          delay(1)
          awaitError().shouldBeInstanceOf<TimeoutException>()
        }
      }

      test("Timeout first Duration - Triggers") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirst(Duration.ofMillis(10)).test {
          delay(10)
          awaitError().shouldBeInstanceOf<TimeoutException>()
        }
      }

      test("Timeout first - Doesn't trigger") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirst(10).test {
          delay(9)
          expectNoEvents()
          channel.send("S")
          expectMostRecentItem() shouldBe "S"
          delay(10)
          expectNoEvents()
          channel.close()
          awaitComplete()
        }
      }

      test("Timeout first or null - Triggers") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirstOrNull(10).test {
          delay(9)
          expectNoEvents()
          delay(1)
          expectMostRecentItem() shouldBe null
          channel.send("S")
          expectMostRecentItem() shouldBe "S"
          delay(10)
          expectNoEvents()
          channel.close()
          awaitComplete()
        }
      }

      test("Timeout first or null Duration - Triggers") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirstOrNull(Duration.ofMillis(10)).test {
          delay(10)
          expectMostRecentItem() shouldBe null
          channel.close()
          awaitComplete()
        }
      }

      test("Timeout first or null - Doesn't trigger") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirstOrNull(10).test {
          delay(9)
          expectNoEvents()
          channel.send("S")
          expectMostRecentItem() shouldBe "S"
          delay(10)
          expectNoEvents()
          channel.close()
          awaitComplete()
        }
      }

      test("Timeout first or default - Triggers") {
        val channel = Channel<String>()
        channel
            .consumeAsFlow()
            .timeoutFirstOrDefault(10) { "X" }
            .test {
              delay(9)
              expectNoEvents()
              delay(1)
              expectMostRecentItem() shouldBe "X"
              channel.send("S")
              expectMostRecentItem() shouldBe "S"
              channel.close()
              awaitComplete()
            }
      }

      test("Timeout first or default Duration - Triggers") {
        val channel = Channel<String>()
        channel
            .consumeAsFlow()
            .timeoutFirstOrDefault(Duration.ofMillis(10)) { "X" }
            .test {
              delay(10)
              expectMostRecentItem() shouldBe "X"
              channel.close()
              awaitComplete()
            }
      }

      test("Timeout first or default value Duration - Triggers") {
        val channel = Channel<String>()
        channel.consumeAsFlow().timeoutFirstOrDefault(Duration.ofMillis(10), "X").test {
          delay(10)
          expectMostRecentItem() shouldBe "X"
          channel.close()
          awaitComplete()
        }
      }

      test("Timeout first or default - Exception handling") {
        val channel = Channel<String>()
        channel
            .consumeAsFlow()
            .timeoutFirstOrDefault(10) { "X" }
            .test {
              delay(9)
              expectNoEvents()
              channel.close(IllegalArgumentException())
              awaitError()
            }
      }

      test("Timeout first or default - Doesn't trigger") {
        val channel = Channel<String>()
        channel
            .consumeAsFlow()
            .timeoutFirstOrDefault(10) { "X" }
            .test {
              delay(9)
              expectNoEvents()
              channel.send("S")
              expectMostRecentItem() shouldBe "S"
              delay(10)
              expectNoEvents()
              channel.close()
              awaitComplete()
            }
      }
    })
