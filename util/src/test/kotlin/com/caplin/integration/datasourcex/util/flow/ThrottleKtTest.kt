@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow

internal class ThrottleKtTest :
    FunSpec({
      test("Throttle latest") {
        val flow = Channel<String>()
        flow.consumeAsFlow().throttleLatest(100).test {
          flow.send("A")
          expectMostRecentItem() shouldBeEqual "A"
          flow.send("B")
          delay(99)
          expectNoEvents()
          delay(1)
          expectMostRecentItem() shouldBeEqual "B"
          flow.send("X")
          delay(50)
          flow.send("Y")
          delay(50)
          expectMostRecentItem() shouldBeEqual "Y"
          delay(100)
          flow.send("Z")
          expectMostRecentItem() shouldBeEqual "Z"
          flow.close()
          awaitComplete()
        }
      }

      test("Throttle latest emits latest on upstream complete") {
        val flow = Channel<String>()
        flow.consumeAsFlow().throttleLatest(100).test {
          flow.send("A")
          expectMostRecentItem() shouldBeEqual "A"
          flow.send("B")
          flow.send("C")
          expectNoEvents()
          flow.close()
          expectMostRecentItem() shouldBeEqual "C"
          awaitComplete()
        }
      }
    })
