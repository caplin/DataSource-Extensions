@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow

class BufferKtTest :
    FunSpec({
      test("Buffered debounce") {
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
    })
