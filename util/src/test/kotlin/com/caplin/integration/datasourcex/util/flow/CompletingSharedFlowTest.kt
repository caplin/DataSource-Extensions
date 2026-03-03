@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow

class CompletingSharedFlowTest :
    FunSpec({
      test("Completing shared flow - Value propagation") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val sharedFlow =
            upstream
                .receiveAsFlow()
                .dematerialize()
                .shareInCompleting(backgroundScope, SharingStarted.Eagerly)

        sharedFlow.test {
          upstream.send(Value("A"))
          awaitItem() shouldBeEqual "A"

          upstream.send(Value("B"))
          awaitItem() shouldBeEqual "B"
        }
      }

      test("Completing shared flow - Replay") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val sharedFlow =
            upstream
                .receiveAsFlow()
                .dematerialize()
                .shareInCompleting(backgroundScope, SharingStarted.Eagerly, 1)

        upstream.send(Value("A"))

        sharedFlow.test { awaitItem() shouldBeEqual "A" }
      }

      test("Completing shared flow - Completion propagation") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val sharedFlow =
            upstream
                .receiveAsFlow()
                .dematerialize()
                .shareInCompleting(backgroundScope, SharingStarted.Eagerly)

        sharedFlow.test {
          upstream.send(Completion())
          awaitComplete()
        }
      }

      test("Completing shared flow - Error propagation") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val sharedFlow =
            upstream
                .receiveAsFlow()
                .dematerialize()
                .shareInCompleting(backgroundScope, SharingStarted.Eagerly)

        sharedFlow.test {
          upstream.send(Completion(IllegalArgumentException()))
          awaitError().shouldBeInstanceOf<IllegalArgumentException>()
        }
      }

      test("Completing shared flow - Upstream restart") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val sharedFlow =
            upstream
                .receiveAsFlow()
                .dematerialize()
                .shareInCompleting(backgroundScope, SharingStarted.WhileSubscribed())

        sharedFlow.test {
          upstream.send(Value("A"))
          awaitItem() shouldBeEqual "A"

          upstream.send(Completion())
          awaitComplete()
        }

        sharedFlow.test {
          upstream.send(Value("B"))
          awaitItem() shouldBeEqual "B"
        }
      }

      test("Shared flow cache") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val sharedFlowCache =
            CompletingSharedFlowCache<String, String>(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )

        sharedFlowCache
            .get("A") { upstream.receiveAsFlow().dematerialize() }
            .test {
              expectNoEvents()

              upstream.send(Value("X"))
              awaitItem() shouldBeEqual "X"

              upstream.send(Completion())
              awaitComplete()
            }
      }

      test("Shared flow cache with multiple keys") {
        val aUpstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val bUpstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)

        val sharedFlowCache =
            CompletingSharedFlowCache<String, String>(
                    scope = backgroundScope,
                    started = SharingStarted.WhileSubscribed(),
                    replay = 1,
                )
                .loading { key ->
                  when (key) {
                    "A" -> aUpstream.receiveAsFlow().dematerialize()
                    "B" -> bUpstream.receiveAsFlow().dematerialize()
                    else -> error("Invalid key")
                  }
                }

        turbineScope {
          val aTurbine = sharedFlowCache["A"].testIn(this)
          val bTurbine = sharedFlowCache["B"].testIn(this)

          aUpstream.send(Value("A1"))
          aTurbine.awaitItem() shouldBeEqual "A1"

          bUpstream.send(Value("B1"))
          bTurbine.awaitItem() shouldBeEqual "B1"

          aUpstream.send(Value("A2"))
          aTurbine.awaitItem() shouldBeEqual "A2"

          bUpstream.send(Value("B2"))
          bTurbine.awaitItem() shouldBeEqual "B2"

          aTurbine.expectNoEvents()
          bTurbine.expectNoEvents()

          aUpstream.close()
          bUpstream.close()

          aTurbine.awaitComplete()
          bTurbine.awaitComplete()
        }
      }

      test("MutableCompletingSharedFlow basic operations") {
        val mutableFlow = MutableCompletingSharedFlow<String>(replay = 1)

        mutableFlow.test {
          mutableFlow.tryEmit("A") shouldBeEqual true
          awaitItem() shouldBeEqual "A"

          mutableFlow.emit("B")
          awaitItem() shouldBeEqual "B"

          mutableFlow.complete(IllegalArgumentException("Test error"))
          awaitError().shouldBeInstanceOf<IllegalArgumentException>()
        }

        // Test normal completion
        val mutableFlow2 = MutableCompletingSharedFlow<String>(replay = 1)
        mutableFlow2.test {
          mutableFlow2.emit("A")
          awaitItem() shouldBeEqual "A"
          mutableFlow2.complete()
          awaitComplete()
        }
      }
    })
