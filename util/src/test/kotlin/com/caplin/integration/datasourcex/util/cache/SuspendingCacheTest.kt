package com.caplin.integration.datasourcex.util.cache

import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope

// Real concurrency / real dispatchers, so these opt out of the project's coroutineTestScope.
class SuspendingCacheTest :
    FunSpec({
      val scope = CoroutineScope(Dispatchers.Default)
      afterSpec { scope.cancel() }

      test("concurrent gets for the same key share a single load").config(
          coroutineTestScope = false
      ) {
        val calls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, String>(scope) { key ->
              calls.incrementAndGet()
              gate.await()
              "v:$key"
            }

        coroutineScope {
          val gets = List(20) { async { cache.get("k") } }
          gate.complete(Unit)
          gets.awaitAll().forEach { it shouldBe "v:k" }
        }

        calls.get() shouldBe 1
      }

      test("eviction triggers a read-through reload").config(coroutineTestScope = false) {
        val calls = ConcurrentHashMap<String, Int>()
        val cache =
            Caffeine.newBuilder().maximumSize(1).buildSuspending<String, String>(scope) { key ->
              calls.merge(key, 1, Int::plus)
              "v:$key"
            }

        cache.get("a") shouldBe "v:a"
        cache.get("b") shouldBe "v:b"
        cache.asyncCache().synchronous().cleanUp()

        cache.getIfPresent("a").shouldBeNull()
        cache.get("a") shouldBe "v:a"

        calls["a"] shouldBe 2
      }

      test("a failed load surfaces to the caller without breaking the cache").config(
          coroutineTestScope = false
      ) {
        val cache =
            Caffeine.newBuilder().buildSuspending<String, String>(scope) { key ->
              if (key == "bad") error("boom") else "v:$key"
            }

        shouldThrow<IllegalStateException> { cache.get("bad") }
        cache.get("good") shouldBe "v:good"
      }

      test("put populates without invoking the loader").config(coroutineTestScope = false) {
        val calls = AtomicInteger(0)
        val cache =
            Caffeine.newBuilder().buildSuspending<String, String>(scope) { key ->
              calls.incrementAndGet()
              "loaded:$key"
            }

        cache.put("k", "put")
        cache.get("k") shouldBe "put"

        calls.get() shouldBe 0
      }
    })
