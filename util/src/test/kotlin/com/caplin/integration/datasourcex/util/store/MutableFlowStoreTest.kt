package com.caplin.integration.datasourcex.util.store

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.cache.buildSuspending
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CompletableFuture

class MutableFlowStoreTest :
    FunSpec({
      test("put publishes a versioned delta and updates the cache only on commit") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, CacheEntry<String>>(backgroundScope)
        val store = mutableFlowStore(backing, cache, txContext = inMemoryTxContext)

        store.asFlow().test {
          val tx = InMemoryTx()
          store.put("k", "v", tx)
          expectNoEvents()
          cache.getIfPresent("k").shouldBeNull()

          tx.commit()
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v", 1L)
          cache.getIfPresent("k") shouldBe Live("v", 1L)
        }
      }

      test("rollback publishes nothing and the next write gets a strictly greater version") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, CacheEntry<String>>(backgroundScope)
        val store = mutableFlowStore(backing, cache, txContext = inMemoryTxContext)

        store.asFlow().test {
          val tx = InMemoryTx()
          store.put("k", "v1", tx)
          tx.rollback()
          expectNoEvents()

          val tx2 = InMemoryTx()
          store.put("k", "v2", tx2)
          tx2.commit()
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v2", 2L)
        }
      }

      test("a writer failure propagates and leaves the cache and stream untouched") {
        val backing = mockk<CacheLoaderWriter<String, String, InMemoryTx>>()
        every { backing.write(any(), any(), any()) } throws RuntimeException("boom")
        val cache =
            Caffeine.newBuilder().buildSuspending<String, CacheEntry<String>>(backgroundScope)
        val store = mutableFlowStore(backing, cache, txContext = inMemoryTxContext)

        store.asFlow().test {
          shouldThrow<RuntimeException> { store.put("k", "v", InMemoryTx()) }
          expectNoEvents()
          cache.getIfPresent("k").shouldBeNull()
        }
      }

      test("get reflects the committed value only after commit") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, CacheEntry<String>>(backgroundScope)
        val store = mutableFlowStore(backing, cache, txContext = inMemoryTxContext)

        InMemoryTx().also {
          store.put("k", "v1", it)
          it.commit()
        }
        store.get("k") shouldBe "v1"

        val tx = InMemoryTx()
        store.put("k", "v2", tx)
        store.get("k") shouldBe "v1"

        tx.commit()
        store.get("k") shouldBe "v2"
      }

      test("get(key, tx) reads through the store within the transaction, bypassing the cache") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        backing.seed("k", "fresh-in-db", 9L)
        val cache =
            Caffeine.newBuilder().buildSuspending<String, CacheEntry<String>>(backgroundScope)
        // A stale, lower-versioned entry sits in the cache.
        cache.asyncCache().put("k", CompletableFuture.completedFuture(Live("stale-in-cache", 1L)))
        val store = mutableFlowStore(backing, cache, txContext = inMemoryTxContext)

        store.get("k") shouldBe "stale-in-cache" // cache-first
        store.get("k", InMemoryTx()) shouldBe "fresh-in-db" // bypasses the cache, reads the store
      }

      test("remove publishes a Removed delta and tombstones the cache") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, CacheEntry<String>>(backgroundScope)
        val store = mutableFlowStore(backing, cache, txContext = inMemoryTxContext)

        store.asFlow().test {
          InMemoryTx().also {
            store.put("k", "v1", it)
            it.commit()
          }
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v1", 1L)

          InMemoryTx().also {
            store.remove("k", it)
            it.commit()
          }
          awaitItem() shouldBe VersionedMapEvent.Removed("k", 2L)
          cache.getIfPresent("k") shouldBe Tombstone(2L)
          store.get("k").shouldBeNull()
        }
      }
    })
