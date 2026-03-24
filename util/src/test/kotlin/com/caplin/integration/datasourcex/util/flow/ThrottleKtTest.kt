@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

internal class ThrottleKtTest :
    FunSpec({
      test("Throttle latest") {
        val flow = Channel<String>()
        flow.consumeAsFlow().throttleLatest(100).test {
          flow.send("A")
          awaitItem() shouldBeEqual "A"
          flow.send("B")
          delay(99)
          expectNoEvents()
          delay(1)
          awaitItem() shouldBeEqual "B"
          flow.send("X")
          delay(50)
          flow.send("Y")
          delay(50)
          awaitItem() shouldBeEqual "Y"
          delay(100)
          flow.send("Z")
          awaitItem() shouldBeEqual "Z"
          flow.close()
          awaitComplete()
        }
      }

      test("Throttle latest emits latest on upstream complete") {
        val flow = Channel<String>()
        flow.consumeAsFlow().throttleLatest(100).test {
          flow.send("A")
          awaitItem() shouldBeEqual "A"
          flow.send("B")
          flow.send("C")
          expectNoEvents()
          flow.close()
          awaitItem() shouldBeEqual "C"
          awaitComplete()
        }
      }

      test("Throttle latest with empty flow") {
        emptyFlow<String>().throttleLatest(100).test { awaitComplete() }
      }

      test("Throttle latest with error") {
        flow {
              emit("A")
              delay(10)
              throw RuntimeException("Failure")
            }
            .throttleLatest(100)
            .test {
              awaitItem() shouldBeEqual "A"
              awaitError().message shouldBe "Failure"
            }
      }

      test("Throttle latest drops intermediate items") {
        val flow = Channel<String>()
        flow.consumeAsFlow().throttleLatest(100).test {
          flow.send("1")
          awaitItem() shouldBeEqual "1"

          flow.send("2")
          flow.send("3")
          flow.send("4")

          delay(150)
          awaitItem() shouldBeEqual "4"

          flow.close()
          awaitComplete()
        }
      }

      test("Throttle latest slow upstream") {
        val flow = Channel<String>()
        flow.consumeAsFlow().throttleLatest(100).test {
          flow.send("A")
          awaitItem() shouldBeEqual "A"

          delay(150)
          flow.send("B")
          awaitItem() shouldBeEqual "B"

          delay(150)
          flow.send("C")
          awaitItem() shouldBeEqual "C"

          flow.close()
          awaitComplete()
        }
      }
    })
