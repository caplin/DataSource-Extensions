@file:OptIn(DelicateCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow

class ValueOrCompletionTest :
    FunSpec({
      test("Naturally completing materialize unboxed") {
        flow {
              emit("a")
              emit("b")
            }
            .materializeUnboxed()
            .test {
              awaitItem().shouldNotBeNull() shouldBeEqual "a"
              awaitItem().shouldNotBeNull() shouldBeEqual "b"
              awaitItem().shouldNotBeNull() shouldBeEqual Completion(null)
              awaitComplete()
            }
      }

      test("Throw completing materialize unboxed") {
        flow {
              emit("a")
              emit("b")
              error("aah")
            }
            .materializeUnboxed()
            .test {
              awaitItem().shouldNotBeNull() shouldBeEqual "a"
              awaitItem().shouldNotBeNull() shouldBeEqual "b"
              awaitItem()
                  .shouldBeInstanceOf<Completion>()
                  .throwable
                  .shouldBeInstanceOf<IllegalStateException>()
              awaitComplete()
            }
      }

      test("Downstream cancel completing materialize unboxed") {
        flow {
              emit("a")
              emit("b")
              awaitCancellation()
            }
            .materializeUnboxed()
            .test {
              awaitItem().shouldNotBeNull() shouldBeEqual "a"
              awaitItem().shouldNotBeNull() shouldBeEqual "b"
              cancelAndIgnoreRemainingEvents()
            }
      }

      test("Naturally completing unboxed") {
        flow {
              emit("a")
              emit("b")
            }
            .materializeUnboxed()
            .dematerializeUnboxed<String>()
            .test {
              awaitItem() shouldBeEqual "a"
              awaitItem() shouldBeEqual "b"
              awaitComplete()
            }
      }

      test("Throw completing unboxed") {
        flow {
              emit("a")
              emit("b")
              error("aah")
            }
            .materializeUnboxed()
            .dematerializeUnboxed<String>()
            .test {
              awaitItem() shouldBeEqual "a"
              awaitItem() shouldBeEqual "b"
              awaitError().shouldBeInstanceOf<IllegalStateException>()
            }
      }

      test("Naturally completing materialize boxed") {
        flow {
              emit("a")
              emit("b")
            }
            .materialize()
            .test {
              awaitItem() shouldBeEqual Value("a")
              awaitItem() shouldBeEqual Value("b")
              awaitItem() shouldBeEqual Completion(null)
              awaitComplete()
            }
      }

      test("Throw completing materialize boxed") {
        flow {
              emit("a")
              emit("b")
              error("aah")
            }
            .materialize()
            .test {
              awaitItem() shouldBeEqual Value("a")
              awaitItem() shouldBeEqual Value("b")
              awaitItem()
                  .shouldBeInstanceOf<Completion>()
                  .throwable
                  .shouldBeInstanceOf<IllegalStateException>()
              awaitComplete()
            }
      }

      test("Downstream cancel completing materialize boxed") {
        flow {
              emit("a")
              emit("b")
              awaitCancellation()
            }
            .materialize()
            .test {
              awaitItem() shouldBeEqual Value("a")
              awaitItem() shouldBeEqual Value("b")
              cancelAndIgnoreRemainingEvents()
            }
      }

      test("Naturally completing boxed") {
        flow {
              emit("a")
              emit("b")
            }
            .materialize()
            .dematerialize()
            .test {
              awaitItem() shouldBeEqual "a"
              awaitItem() shouldBeEqual "b"
              awaitComplete()
            }
      }

      test("Throw completing boxed") {
        flow {
              emit("a")
              emit("b")
              error { "aah" }
            }
            .materialize()
            .dematerialize()
            .test {
              awaitItem() shouldBeEqual "a"
              awaitItem() shouldBeEqual "b"
              awaitError().shouldNotBeNull().shouldBeInstanceOf<IllegalStateException>()
            }
      }
    })
