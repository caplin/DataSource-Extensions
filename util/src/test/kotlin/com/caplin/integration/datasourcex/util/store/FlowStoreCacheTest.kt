package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger

class FlowStoreCacheTest :
    FunSpec({
      fun newCache() = FlowStoreCache<String, String>(Caffeine.newBuilder().build())

      test("getOrLoad caches the loaded value and serves it cache-first") {
        val calls = AtomicInteger(0)
        val cache = newCache()
        val load = { k: String ->
          calls.incrementAndGet()
          Versioned("v:$k", 1L)
        }

        cache.getOrLoad("k", load) shouldBe Live("v:k", 1L)
        cache.getOrLoad("k", load) shouldBe Live("v:k", 1L)
        calls.get() shouldBe 1
      }

      test("a load that finds nothing caches nothing") {
        val cache = newCache()

        cache.getOrLoad("missing") { null }.shouldBeNull()
        cache.getIfPresent("missing").shouldBeNull()
      }

      test("putIfNewer keeps the strictly-newer resident entry") {
        val cache = newCache()

        cache.putIfNewer("k", Live("v5", 5L)) shouldBe Live("v5", 5L)
        cache.putIfNewer("k", Live("v3", 3L)) shouldBe Live("v5", 5L) // older rejected
        cache.putIfNewer("k", Live("v5b", 5L)) shouldBe Live("v5", 5L) // equal rejected
        cache.putIfNewer("k", Live("v9", 9L)) shouldBe Live("v9", 9L)
      }

      test("reflectIfNewer updates a resident entry only, version-gated") {
        val cache = newCache()

        cache.reflectIfNewer(VersionedMapEvent.Upsert("k", "v1", 1L)) // absent -> no-op
        cache.getIfPresent("k").shouldBeNull()

        cache.putIfNewer("k", Live("v5", 5L))
        cache.reflectIfNewer(VersionedMapEvent.Upsert("k", "v3", 3L)) // older -> ignored
        cache.getIfPresent("k") shouldBe Live("v5", 5L)
        cache.reflectIfNewer(VersionedMapEvent.Removed("k", 6L)) // newer -> tombstone
        cache.getIfPresent("k") shouldBe Tombstone(6L)
      }
    })
