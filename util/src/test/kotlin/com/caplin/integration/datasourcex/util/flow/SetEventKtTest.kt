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
          awaitItem().toSet() shouldBeEqual emptySet()
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
    })
