@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.Populated
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf

class SimpleMapEventKtTest :
    FunSpec({
      test("runningFoldToMap SimpleMapEvents without partials") {
        flowOf(
                Upsert("K", "v1"),
                Upsert("K", "v2"),
                Upsert("K2", "v3"),
                Removed("K"),
                Populated,
                Upsert("K", "v4"))
            .runningFoldToMap()
            .test {
              awaitItem() shouldContainExactly mapOf("K2" to "v3")
              awaitItem() shouldContainExactly mapOf("K2" to "v3", "K" to "v4")
              awaitComplete()
            }
      }

      test("runningFoldToMap SimpleMapEvents with partials") {
        flowOf(
                Upsert("K", "v1"),
                Upsert("K", "v2"),
                Upsert("K2", "v3"),
                Removed("K"),
                Populated,
                Upsert("K", "v4"))
            .runningFoldToMap(emitPartials = true)
            .test {
              awaitItem() shouldContainExactly mapOf("K" to "v1")
              awaitItem() shouldContainExactly mapOf("K" to "v2")
              awaitItem() shouldContainExactly mapOf("K" to "v2", "K2" to "v3")
              awaitItem() shouldContainExactly mapOf("K2" to "v3")
              awaitItem() shouldContainExactly mapOf("K2" to "v3", "K" to "v4")
              awaitComplete()
            }
      }

      test("runningFoldToMap SimpleMapEvents removing non existing key") {
        flowOf(Upsert("K", "v1"), Removed("K"), Removed("K"), Populated).runningFoldToMap().test {
          awaitError().shouldBeInstanceOf<IllegalStateException>()
        }
      }

      test("runningFoldToMap EntryEvents") {
        flowOf(
                Upsert("K", "v1"),
                Upsert("K", "v2"),
                Upsert("K2", "v3"),
                Removed("K"),
                Upsert("K", "v4"))
            .runningFoldToMap()
            .test {
              awaitItem() shouldContainExactly mapOf("K" to "v1")
              awaitItem() shouldContainExactly mapOf("K" to "v2")
              awaitItem() shouldContainExactly mapOf("K" to "v2", "K2" to "v3")
              awaitItem() shouldContainExactly mapOf("K2" to "v3")
              awaitItem() shouldContainExactly mapOf("K2" to "v3", "K" to "v4")
              awaitComplete()
            }
      }

      test("runningFoldToMap EntryEvents removing non existing key") {
        flowOf(Upsert("K", "v1"), Removed("K"), Removed("K")).runningFoldToMap().test {
          awaitItem() shouldContainExactly mapOf("K" to "v1")
          awaitItem() shouldContainExactly mapOf()
          awaitError().shouldBeInstanceOf<IllegalStateException>()
        }
      }
    })
