package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow

class MapEventKtTest :
    FunSpec({
      test("runningFoldToMap emits map state") {
        flowOf(Upsert("K1", null, "V1"), Upsert("K2", null, "V2"), Populated, Removed("K1", "V1"))
            .runningFoldToMap()
            .test {
              awaitItem() shouldContainExactly mapOf("K1" to "V1", "K2" to "V2")
              awaitItem() shouldContainExactly mapOf("K2" to "V2")
              awaitComplete()
            }
      }

      test("runningFoldToMap with partials emits intermediate map states") {
        flowOf(Upsert("K1", null, "V1"), Upsert("K2", null, "V2"), Populated, Removed("K1", "V1"))
            .runningFoldToMap(emitPartials = true)
            .test {
              awaitItem() shouldContainExactly emptyMap()
              awaitItem() shouldContainExactly mapOf("K1" to "V1")
              awaitItem() shouldContainExactly mapOf("K1" to "V1", "K2" to "V2")
              awaitItem() shouldContainExactly mapOf("K2" to "V2")
              awaitComplete()
            }
      }

      test("conflateKeys collapses events for same key") {
        val channel = Channel<MapEvent<String, String>>(Channel.UNLIMITED)
        channel.trySend(Upsert("K1", null, "V1"))
        channel.trySend(Upsert("K2", null, "V2"))
        channel.trySend(Upsert("K1", "V1", "V1_NEW"))
        channel.trySend(Removed("K2", "V2"))
        channel.trySend(Upsert("K3", null, "V3"))
        channel.trySend(Populated)
        channel.close()

        // We buffer(0) to ensure the conflateKeys actor has a chance to process all items
        // from the unlimited channel before the collector starts taking them.
        channel.receiveAsFlow().conflateKeys().buffer(0).test {
          awaitItem() shouldBeEqual Upsert("K1", null, "V1_NEW")
          awaitItem() shouldBeEqual Upsert("K3", null, "V3")
          awaitItem() shouldBeEqual Populated
          awaitComplete()
        }
      }

      test("conflateKeys handles Removed correctly") {
        val channel = Channel<MapEvent<String, String>>(Channel.UNLIMITED)
        channel.trySend(Upsert("K1", "V0", "V1"))
        channel.trySend(Removed("K1", "V1"))
        channel.trySend(Populated)
        channel.close()

        channel.receiveAsFlow().conflateKeys().buffer(0).test {
          awaitItem() shouldBeEqual Removed("K1", "V0")
          awaitItem() shouldBeEqual Populated
          awaitComplete()
        }
      }
    })
