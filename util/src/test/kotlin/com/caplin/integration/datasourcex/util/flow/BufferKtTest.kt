@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow

class BufferKtTest :
    FunSpec({
      test("Buffered debounce with millis") {
        val channel = Channel<String>(Channel.BUFFERED)
        channel.consumeAsFlow().bufferingDebounce(10).test {
          delay(1)
          channel.send("A")
          delay(11)
          awaitItem() shouldBeEqual listOf("A")
          channel.send("B")
          delay(5)
          channel.send("C")
          delay(5)
          channel.send("D")
          delay(11)
          awaitItem() shouldBeEqual listOf("B", "C", "D")
          channel.send("E")
          delay(9)
          channel.send("F")
          awaitItem() shouldBeEqual listOf("E", "F")
          channel.close()
          awaitComplete()
        }
      }

      test("Buffered debounce with Duration") {
        val channel = Channel<String>(Channel.BUFFERED)
        channel.consumeAsFlow().bufferingDebounce(Duration.ofMillis(10)).test {
          delay(1)
          channel.send("A")
          delay(11)
          awaitItem() shouldBeEqual listOf("A")
          channel.close()
          awaitComplete()
        }
      }

      test("Buffered debounce upstream error propagation") {
        val channel = Channel<String>(Channel.BUFFERED)
        channel.receiveAsFlow().bufferingDebounce(10).test {
          channel.send("A")
          delay(50) // Ensure "A" is processed into bufferedItems
          channel.close(IllegalArgumentException("test error"))
          awaitItem() shouldBeEqual listOf("A")
          awaitError().shouldBeInstanceOf<IllegalArgumentException>()
        }
      }

      test("Buffered debounce immediate emit on completion") {
        val channel = Channel<String>(Channel.BUFFERED)
        channel.consumeAsFlow().bufferingDebounce(1000).test {
          channel.send("A")
          channel.send("B")
          channel.close()
          awaitItem() shouldBeEqual listOf("A", "B")
          awaitComplete()
        }
      }
    })
