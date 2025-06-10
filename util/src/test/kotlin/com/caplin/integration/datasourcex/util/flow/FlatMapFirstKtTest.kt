@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FlatMapFirstKtTest :
    FunSpec({
      test("flatMapFirst") {
        flowOf("A", "B", "C")
            .flatMapFirst { first -> flowOf(first, "X", "Y", "Z") }
            .test {
              awaitItem() shouldBeEqual "A"
              awaitItem() shouldBeEqual "X"
              awaitItem() shouldBeEqual "Y"
              awaitItem() shouldBeEqual "Z"
              awaitComplete()
            }
      }

      test("flatMapFirst with upstream") {
        flowOf("A", "B", "C")
            .flatMapFirst { first, upstream -> upstream.map { first + it } }
            .test {
              awaitItem() shouldBeEqual "AA"
              awaitItem() shouldBeEqual "AB"
              awaitItem() shouldBeEqual "AC"
              awaitComplete()
            }
      }

      test("flatMapFirst with upstream empty") {
        flow<String> {}
            .flatMapFirst { first, upstream -> upstream.map { first + it } }
            .test { awaitComplete() }
      }

      test("flatMapFirst with upstream exception") {
        flow<String> { throw IllegalArgumentException() }
            .flatMapFirst { first, upstream -> upstream.map { first + it } }
            .test { awaitError().shouldBeInstanceOf<IllegalArgumentException>() }

        flow {
              emit("A")
              throw IllegalArgumentException()
            }
            .flatMapFirst { first, upstream -> upstream.map { first + it } }
            .test { awaitError().shouldBeInstanceOf<IllegalArgumentException>() }
      }
    })
