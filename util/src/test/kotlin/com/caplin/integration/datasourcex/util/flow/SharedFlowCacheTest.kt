@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

class SharedFlowCacheTest :
    FunSpec({
      test("shares a single upstream across subscribers") {
        val collections = AtomicInteger(0)
        val upstream = Channel<String>(Channel.BUFFERED)
        val cache =
            sharedFlowCache<String, String>(backgroundScope, SharingStarted.WhileSubscribed(), 1)
        val supplier = { _: String ->
          flow {
            collections.incrementAndGet()
            emitAll(upstream.receiveAsFlow())
          }
        }
        val got = mutableListOf<String>()

        val shared = cache["K", supplier]
        backgroundScope.launch { shared.collect { got.add("a:$it") } }
        backgroundScope.launch { shared.collect { got.add("b:$it") } }
        delay(100.milliseconds)

        upstream.send("X")
        delay(100.milliseconds)

        got shouldContainExactlyInAnyOrder listOf("a:X", "b:X")
        collections.get() shouldBeEqual 1
      }

      test("evicts a key once all subscribers leave") {
        val upstream = Channel<String>(Channel.BUFFERED)
        val cache =
            sharedFlowCache<String, String>(backgroundScope, SharingStarted.WhileSubscribed(), 1)
        val supplier = { _: String -> upstream.receiveAsFlow() }

        val first = cache["K", supplier]
        val subscriber = backgroundScope.launch { first.collect {} }
        delay(100.milliseconds)

        subscriber.cancelAndJoin()
        delay(100.milliseconds)

        // The only subscriber has gone, so the key is evicted and a fresh get builds a new entry.
        cache["K", supplier] shouldNotBeSameInstanceAs first
      }

      test("an upstream error on one key does not affect other keys") {
        val errors = mutableListOf<Throwable>()
        val cacheScope =
            CoroutineScope(
                backgroundScope.coroutineContext +
                    CoroutineExceptionHandler { _, e -> errors.add(e) }
            )
        val cache = sharedFlowCache<String, String>(cacheScope, SharingStarted.WhileSubscribed(), 1)
        val good = Channel<String>(Channel.BUFFERED)
        val gotGood = mutableListOf<String>()

        backgroundScope.launch {
          cache["good", { good.receiveAsFlow() }].collect { gotGood.add(it) }
        }
        backgroundScope.launch { cache["bad", { flow { error("boom") } }].collect {} }
        delay(100.milliseconds)

        good.send("X")
        delay(100.milliseconds)

        // The "bad" key's sharing coroutine failed in isolation; "good" keeps working.
        gotGood shouldContainExactly listOf("X")
      }

      test("completing adapter propagates completion") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val cache: CompletingSharedFlowCache<String, String> =
            completingSharedFlowCache(backgroundScope, SharingStarted.WhileSubscribed(), 1)

        cache["A", { upstream.receiveAsFlow().dematerialize() }].test {
          upstream.send(Value("X"))
          awaitItem() shouldBeEqual "X"
          upstream.send(Completion())
          awaitComplete()
        }
      }

      test("completing adapter propagates errors") {
        val upstream = Channel<ValueOrCompletion<String>>(Channel.BUFFERED)
        val cache: CompletingSharedFlowCache<String, String> =
            completingSharedFlowCache(backgroundScope, SharingStarted.WhileSubscribed(), 1)

        cache["A", { upstream.receiveAsFlow().dematerialize() }].test {
          upstream.send(Completion(IllegalArgumentException()))
          awaitError().shouldBeInstanceOf<IllegalArgumentException>()
        }
      }

      test("completing adapter supports downstream retry after an upstream error") {
        val attempts = AtomicInteger(0)
        val cache: CompletingSharedFlowCache<String, String> =
            completingSharedFlowCache(backgroundScope, SharingStarted.WhileSubscribed(), 1)
        val supplier = { _: String ->
          flow {
            if (attempts.getAndIncrement() == 0) error("boom")
            emit("ok")
          }
        }

        // The first attempt errors; the entry is evicted as the terminal is published, so retry
        // re-resolves to a fresh entry, re-runs the supplier, and succeeds - no back-off needed.
        cache["K", supplier]
            .retry(3) { it is IllegalStateException }
            .test {
              awaitItem() shouldBeEqual "ok"
              awaitComplete()
            }

        attempts.get() shouldBeEqual 2
      }
    })
