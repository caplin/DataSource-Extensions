package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.flow.flowOf

class CastKtTest :
    FunSpec({
      test("cast successfully casts items") {
        val flow = flowOf(1, 2, 3)
        flow.cast<Number>().test {
          awaitItem() shouldBeEqual 1
          awaitItem() shouldBeEqual 2
          awaitItem() shouldBeEqual 3
          awaitComplete()
        }
      }

      test("cast throws ClassCastException on failure when accessed") {
        val flow = flowOf(1, "string")
        val castFlow = flow.cast<Int>()
        shouldThrow<ClassCastException> {
          castFlow.collect {
            // The cast happens here because 'it' is typed as Int
            @Suppress("UNUSED_VARIABLE") val i: Int = it
          }
        }
      }
    })
