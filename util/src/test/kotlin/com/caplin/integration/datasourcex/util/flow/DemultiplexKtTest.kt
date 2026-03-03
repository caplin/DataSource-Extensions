@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

class DemultiplexKtTest :
    FunSpec({
      test("demultiplexBy routes elements by key") {
        val channel = Channel<String>(Channel.UNLIMITED)
        channel
            .receiveAsFlow()
            .demultiplexBy(
                keySelector = { it.first().toString() },
                flowProducer = { key, flow -> flow.map { "$key-$it" }.collect { emit(it) } },
            )
            .test {
              channel.send("Apple")
              awaitItem() shouldBeEqual "A-Apple"
              channel.send("Banana")
              awaitItem() shouldBeEqual "B-Banana"
              channel.send("Avocado")
              awaitItem() shouldBeEqual "A-Avocado"
              cancelAndIgnoreRemainingEvents()
            }
      }

      test("demultiplexBy ignores null keys") {
        val channel = Channel<String>(Channel.UNLIMITED)
        channel
            .receiveAsFlow()
            .demultiplexBy(
                keySelector = { if (it.startsWith("A")) "A" else null },
                flowProducer = { key, flow -> flow.map { "$key-$it" }.collect { emit(it) } },
            )
            .test {
              channel.send("Apple")
              awaitItem() shouldBeEqual "A-Apple"
              channel.send("Banana") // ignored
              channel.send("Avocado")
              awaitItem() shouldBeEqual "A-Avocado"
              cancelAndIgnoreRemainingEvents()
            }
      }
    })
