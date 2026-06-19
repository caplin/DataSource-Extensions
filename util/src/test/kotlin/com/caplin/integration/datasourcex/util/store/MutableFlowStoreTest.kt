package com.caplin.integration.datasourcex.util.store

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun <K : Any, V : Any> newStore(
    backing: Store<K, V, InMemoryTx>,
    cache: Cache<K, CacheEntry<V>?>,
) = MutableFlowStoreImpl(backing, FlowStoreCache(cache), Dispatchers.IO, inMemoryTxContext)

class MutableFlowStoreTest :
    FunSpec({
      test("put publishes a versioned delta and updates the cache only on commit") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

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

      test(
          "publish updates the cache before emitting, so a delta observer never sees a stale cache"
      ) {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        // An unconfined collector resumes synchronously inside the emitting tryEmit, so it captures
        // the cache exactly as of the emit. The cache must already reflect the delta — otherwise a
        // valueFlow subscribing in that window would seed from a stale entry and lose the update.
        val cacheAtEmit = CompletableDeferred<CacheEntry<String>?>()
        val collector =
            CoroutineScope(Dispatchers.Unconfined).launch {
              store.asFlow().collect { event ->
                cacheAtEmit.complete(cache.getIfPresent(event.key))
              }
            }

        InMemoryTx().also {
          store.put("k", "v", it)
          it.commit()
        }

        cacheAtEmit.await() shouldBe Live("v", 1L)
        collector.cancel()
      }

      test("rollback publishes nothing and the next write gets a strictly greater version") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

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
        val backing = mockk<Store<String, String, InMemoryTx>>()
        every { backing.write(any(), any(), any()) } throws RuntimeException("boom")
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.asFlow().test {
          shouldThrow<RuntimeException> { store.put("k", "v", InMemoryTx()) }
          expectNoEvents()
          cache.getIfPresent("k").shouldBeNull()
        }
      }

      test("get reflects the committed value only after commit") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

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
        val backing = InMemoryStore<String, String>()
        backing.seed("k", "fresh-in-db", 9L)
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        // A stale, lower-versioned entry sits in the cache.
        cache.put("k", Live("stale-in-cache", 1L))
        val store = newStore(backing, cache)

        store.get("k") shouldBe "stale-in-cache" // cache-first
        store.get("k", InMemoryTx()) shouldBe "fresh-in-db" // bypasses the cache, reads the store
      }

      test("remove publishes a Removed delta and tombstones the cache") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

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

      test("putAll publishes a delta per entry on commit") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.asFlow().test {
          val tx = InMemoryTx()
          store.putAll(mapOf("a" to "A", "b" to "B"), tx)
          expectNoEvents()

          tx.commit()
          setOf(awaitItem(), awaitItem()) shouldBe
              setOf(
                  VersionedMapEvent.Upsert("a", "A", 1L),
                  VersionedMapEvent.Upsert("b", "B", 2L),
              )
        }
      }

      test("putAll fails before commit when the writer omits a version, publishing nothing") {
        val backing = mockk<Store<String, String, InMemoryTx>>()
        every { backing.writeAll(any(), any()) } returns mapOf("a" to 1L) // "b" missing
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.asFlow().test {
          val tx = InMemoryTx()
          shouldThrow<IllegalStateException> { store.putAll(mapOf("a" to "A", "b" to "B"), tx) }
          tx.commit()
          expectNoEvents()
        }
      }

      test("async.get short-circuits a cache hit and reads through on a miss") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        InMemoryTx().also {
          store.put("k", "v1", it)
          it.commit()
        }

        store.async.get("k") shouldBe "v1"
        backing.loadCount.get() shouldBe 0 // served from cache, no read-through

        store.async.get("missing").shouldBeNull()
        backing.loadCount.get() shouldBe 1 // miss dispatched one read-through
      }

      test("async mutations write through and publish on commit") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.asFlow().test {
          val tx = InMemoryTx()
          store.async.put("k", "v1", tx)
          expectNoEvents()

          tx.commit()
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v1", 1L)
          store.async.get("k") shouldBe "v1"
        }
      }

      test("get(key, tx) returns null for an absent key") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.get("missing", InMemoryTx()).shouldBeNull()
      }

      test("async putAll and remove write through and publish on commit") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.asFlow().test {
          val tx = InMemoryTx()
          store.async.putAll(mapOf("a" to "A", "b" to "B"), tx)
          expectNoEvents()
          tx.commit()
          setOf(awaitItem(), awaitItem()) shouldBe
              setOf(
                  VersionedMapEvent.Upsert("a", "A", 1L),
                  VersionedMapEvent.Upsert("b", "B", 2L),
              )

          val tx2 = InMemoryTx()
          store.async.remove("a", tx2)
          tx2.commit()
          awaitItem() shouldBe VersionedMapEvent.Removed("a", 3L)
        }
      }

      test("async.get(key, tx) reads through within the transaction") {
        val backing = InMemoryStore<String, String>()
        backing.seed("k", "fresh-in-db", 9L)
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.async.get("k", InMemoryTx()) shouldBe "fresh-in-db"
        store.async.asFlow().replayCache shouldBe emptyList() // delegates to the delta stream
      }

      test("async.valueFlow follows the mutable store") {
        val backing = InMemoryStore<String, String>()
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val store = newStore(backing, cache)

        store.async.valueFlow("k").test {
          awaitItem().shouldBeNull()
          InMemoryTx().also {
            store.put("k", "v1", it)
            it.commit()
          }
          awaitItem() shouldBe "v1"
        }
      }
    })
