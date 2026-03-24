@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Insert
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.SetEvent.Populated
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class SetEventKtTest :
    FunSpec({
      test("toEvents converts sets to events") {
        flowOf(setOf("A", "B"), setOf("B", "C")).toEvents().test {
          awaitItem() shouldBeEqual Insert("A")
          awaitItem() shouldBeEqual Insert("B")
          awaitItem() shouldBeEqual Populated
          awaitItem() shouldBeEqual Removed("A")
          awaitItem() shouldBeEqual Insert("C")
          awaitComplete()
        }
      }

      test("runningFoldToSet folds events back to sets") {
        flowOf(Insert("A"), Insert("B"), Populated, Removed("A"), Insert("C"))
            .runningFoldToSet()
            .test {
              awaitItem().toSet() shouldBeEqual setOf("A", "B")
              awaitItem().toSet() shouldBeEqual setOf("B")
              awaitItem().toSet() shouldBeEqual setOf("B", "C")
              awaitComplete()
            }
      }

      test("runningFoldToSet with emitPartials") {
        flowOf(Insert("A"), Insert("B"), Populated).runningFoldToSet(emitPartials = true).test {
          awaitItem().toSet() shouldBeEqual emptySet<String>()
          awaitItem().toSet() shouldBeEqual setOf("A")
          awaitItem().toSet() shouldBeEqual setOf("A", "B")
          awaitComplete()
        }
      }

      test("runningFoldToSet with relaxed = false throws on bad events") {
        flowOf(
                Insert("A"),
                Populated,
                Insert("A"), // relaxed = false should throw because it already exists
            )
            .runningFoldToSet(relaxed = false)
            .test {
              awaitItem().toSet() shouldBeEqual setOf("A")
              awaitError().shouldBeInstanceOf<IllegalStateException>()
            }
      }

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
