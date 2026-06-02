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
import io.mockk.coEvery
import io.mockk.mockk

class MutableFlowStoreTest :
    FunSpec({
      test("put publishes a versioned delta and updates the cache only on commit") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, Versioned<String>>(backgroundScope)
        val store = mutableFlowStore(backing, inMemoryTransactionRunner, cache)

        store.asFlow().test {
          val tx = AutoCommitTxContext(Unit)
          store.put("k", "v", tx)
          expectNoEvents()
          cache.getIfPresent("k").shouldBeNull()

          tx.commit()
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v", 1L)
          cache.getIfPresent("k") shouldBe Versioned("v", 1L)
        }
      }

      test("rollback publishes nothing and the next write gets a strictly greater version") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, Versioned<String>>(backgroundScope)
        val store = mutableFlowStore(backing, inMemoryTransactionRunner, cache)

        store.asFlow().test {
          val tx = AutoCommitTxContext(Unit)
          store.put("k", "v1", tx)
          tx.rollback()
          expectNoEvents()

          store.put("k", "v2")
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v2", 2L)
        }
      }

      test("a writer failure propagates and leaves the cache and stream untouched") {
        val writer = mockk<CacheWriter<String, String, Unit>>()
        coEvery { writer.write(any(), any(), any(), any()) } throws RuntimeException("boom")
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, Versioned<String>>(backgroundScope)
        val store = mutableFlowStore(backing, writer, inMemoryTransactionRunner, cache)

        store.asFlow().test {
          shouldThrow<RuntimeException> { store.put("k", "v") }
          expectNoEvents()
          cache.getIfPresent("k").shouldBeNull()
        }
      }

      test("get reflects the committed value only after commit") {
        val backing = InMemoryCacheLoaderWriter<String, String>()
        val cache =
            Caffeine.newBuilder().buildSuspending<String, Versioned<String>>(backgroundScope)
        val store = mutableFlowStore(backing, inMemoryTransactionRunner, cache)

        store.put("k", "v1")
        store.get("k") shouldBe "v1"

        val tx = AutoCommitTxContext(Unit)
        store.put("k", "v2", tx)
        store.get("k") shouldBe "v1"

        tx.commit()
        store.get("k") shouldBe "v2"
      }
    })
