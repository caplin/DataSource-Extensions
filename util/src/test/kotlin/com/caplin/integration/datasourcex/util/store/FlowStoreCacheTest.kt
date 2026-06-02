package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope

// Real concurrency / real dispatchers, so these opt out of the project's coroutineTestScope.
class FlowStoreCacheTest :
    FunSpec({
      val scope = CoroutineScope(Dispatchers.Default)
      afterSpec { scope.cancel() }

      test("concurrent reads on a miss share a single load").config(coroutineTestScope = false) {
        val calls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val cache = Caffeine.newBuilder().buildFlowStoreCache<String, String>(scope)
        val load: suspend (String) -> Versioned<String>? = { key ->
          calls.incrementAndGet()
          gate.await()
          Versioned("v:$key", 1L)
        }

        coroutineScope {
          val reads = List(20) { async { cache.loadIfNewer("k", load) } }
          gate.complete(Unit)
          reads.awaitAll().forEach { it shouldBe Live("v:k", 1L) }
        }

        calls.get() shouldBe 1
      }

      test("a load that finds nothing caches nothing") {
        val cache = Caffeine.newBuilder().buildFlowStoreCache<String, String>(scope)

        cache.loadIfNewer("missing") { null }.shouldBeNull()
        cache.getIfPresent("missing").shouldBeNull()
      }

      test("putIfNewer keeps the strictly-newer resident entry") {
        val cache = Caffeine.newBuilder().buildFlowStoreCache<String, String>(scope)

        cache.putIfNewer("k", Live("v5", 5L)) shouldBe Live("v5", 5L)
        cache.putIfNewer("k", Live("v3", 3L)) shouldBe Live("v5", 5L) // older rejected
        cache.putIfNewer("k", Live("v5b", 5L)) shouldBe Live("v5", 5L) // equal rejected
        cache.putIfNewer("k", Live("v9", 9L)) shouldBe Live("v9", 9L)
      }

      test("reflectIfNewer updates the cache during an in-flight load, preventing regression").config(
          coroutineTestScope = false
      ) {
        val cache = Caffeine.newBuilder().buildFlowStoreCache<String, String>(scope)
        val loadGate = CompletableDeferred<Unit>()
        val resultGate = CompletableDeferred<Unit>()

        val loadJob = async {
          cache.loadIfNewer("k") {
            loadGate.complete(Unit)
            resultGate.await()
            Versioned("v5", 5L)
          }
        }

        // Wait until the load is executing and in-flight
        loadGate.await()

        // Call reflectIfNewer with a newer version (Version 6) while load is in-flight
        cache.reflectIfNewer(VersionedMapEvent.Upsert("k", "v6", 6L))

        // Complete the load (which yields Version 5)
        resultGate.complete(Unit)
        loadJob.await()

        // Verify that the cache keeps the newer Version 6 and did not regress to Version 5
        cache.getIfPresent("k") shouldBe Live("v6", 6L)
      }

      test("loadIfNewer throws CancellationException if the scope is cancelled").config(
          coroutineTestScope = false
      ) {
        val deadScope = CoroutineScope(Dispatchers.Default)
        deadScope.cancel()

        val cache = Caffeine.newBuilder().buildFlowStoreCache<String, String>(deadScope)

        shouldThrow<CancellationException> {
          cache.loadIfNewer("k") { Versioned("v", 1L) }
        }
      }
    })
