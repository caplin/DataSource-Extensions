@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.plus

class CompletingSharedFlowTest :
    FunSpec({
      test("Share in completing") {
        val upstream = Channel<ValueOrCompletion<String>>()

        val sharedFlow =
            upstream
                .receiveAsFlow()
                .dematerialize()
                .shareInCompleting(
                    scope = this + Job(),
                    started =
                        SharingStarted.WhileSubscribed(
                            stopTimeoutMillis = 100, replayExpirationMillis = 0),
                    replay = 2,
                )

        sharedFlow
            .onSubscription { emit("INITIAL") }
            .test {
              val first = this
              first.awaitItem() shouldBeEqual "INITIAL"
              upstream.send(Value("A"))
              first.awaitItem() shouldBeEqual "A"

              upstream.send(Value("B"))
              first.awaitItem() shouldBeEqual "B"

              sharedFlow.test {
                val second = this
                second.awaitItem() shouldBeEqual "A"
                second.awaitItem() shouldBeEqual "B"

                upstream.send(Value("C"))
                first.awaitItem() shouldBeEqual "C"
                second.awaitItem() shouldBeEqual "C"
              }
            }

        // The replay buffer and upstream subscription still exists as no upstream complete was
        // fired,
        // and we are
        // within the refCount cooldown

        sharedFlow
            .onSubscription { emit("INITIAL") }
            .test {
              awaitItem() shouldBeEqual "INITIAL"
              awaitItem() shouldBeEqual "B"
              awaitItem() shouldBeEqual "C"

              upstream.send(Value("D"))
              awaitItem() shouldBeEqual "D"
            }

        delay(101)

        // The replay buffer was reset and the upstream subscription cancelled as we had no
        // subscribers
        // for longer than
        // the refCount cooldown

        sharedFlow.test {
          expectNoEvents()

          upstream.send(Value("E"))
          awaitItem() shouldBeEqual "E"

          upstream.send(Completion())
          awaitComplete()
        }

        // The upstream complete immediately reset the replay buffer and terminated the upstream
        // subscription
        // despite the refCount cooldown, and we've now initiated a new upstream subscription

        sharedFlow.test {
          expectNoEvents()

          upstream.send(Value("X"))
          awaitItem() shouldBeEqual "X"

          upstream.send(Completion())
          awaitComplete()
        }
      }

      test("Shared flow cache") {
        val upstream = Channel<ValueOrCompletion<String>>()

        val sharedFlowCache =
            CompletingSharedFlowCache<String, String>(
                    scope = this + Job(),
                    started = SharingStarted.WhileSubscribed(100, 0),
                    replay = 2,
                )
                .loading { upstream.receiveAsFlow().dematerialize() }

        sharedFlowCache["A"]
            .onSubscription { emit("INITIAL") }
            .test {
              upstream.send(Value("A"))
              val first = this
              first.awaitItem() shouldBeEqual "INITIAL"
              first.awaitItem() shouldBeEqual "A"

              upstream.send(Value("B"))
              first.awaitItem() shouldBeEqual "B"

              sharedFlowCache["A"].test {
                val second = this
                second.awaitItem() shouldBeEqual "A"
                second.awaitItem() shouldBeEqual "B"

                upstream.send(Value("C"))
                first.awaitItem() shouldBeEqual "C"
                second.awaitItem() shouldBeEqual "C"
              }
            }

        // The replay buffer and upstream subscription still exists as no upstream complete was
        // fired,
        // and we are
        // within the refCount cooldown

        sharedFlowCache["A"].test {
          awaitItem() shouldBeEqual "B"
          awaitItem() shouldBeEqual "C"

          upstream.send(Value("D"))
          awaitItem() shouldBeEqual "D"
        }

        delay(101)

        // The replay buffer was reset and the upstream subscription cancelled as we had no
        // subscribers
        // for longer than
        // the refCount cooldown

        sharedFlowCache["A"].test {
          expectNoEvents()

          upstream.send(Value("E"))
          awaitItem() shouldBeEqual "E"

          upstream.send(Completion())
          awaitComplete()
        }

        // The upstream complete immediately reset the replay buffer and terminated the upstream
        // subscription
        // despite the refCount cooldown, and we've now initiated a new upstream subscription

        sharedFlowCache["A"].test {
          expectNoEvents()

          upstream.send(Value("X"))
          awaitItem() shouldBeEqual "X"

          upstream.send(Completion())
          awaitComplete()
        }
      }
    })
