@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Insert
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Removed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class SetEventKtTest :
    FunSpec({
      test("flatMapLatestAndMerge - When upstream completes first, await inner complete") {
        val setFlow = Channel<Set<String>>()
        val aFlow = Channel<String>()

        setFlow
            .consumeAsFlow()
            .flatMapLatestAndMerge {
              when (it) {
                is Insert ->
                    when (it.value) {
                      "a" -> aFlow.consumeAsFlow()
                      else -> throw IllegalArgumentException(it.value)
                    }
                is Removed -> emptyFlow()
              }
            }
            .test {
              expectNoEvents()

              setFlow.send(setOf("a"))
              expectNoEvents()

              aFlow.send("A")

              awaitItem() shouldBeEqual "A"

              setFlow.close()

              expectNoEvents()

              aFlow.send("B")

              awaitItem() shouldBeEqual "B"

              aFlow.close()

              awaitComplete()
            }
      }

      test("When inner completes first, await upstream complete") {
        val setFlow = Channel<Set<String>>()

        setFlow
            .consumeAsFlow()
            .flatMapLatestAndMerge {
              when (it) {
                is Insert ->
                    when (it.value) {
                      "a" -> flowOf("A")
                      else -> throw IllegalArgumentException(it.value)
                    }
                is Removed -> emptyFlow()
              }
            }
            .test {
              expectNoEvents()

              setFlow.send(setOf("a"))

              awaitItem() shouldBeEqual "A"

              setFlow.close()

              awaitComplete()
            }
      }
    })
