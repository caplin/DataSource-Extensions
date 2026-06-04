package com.caplin.integration.datasourcex.util.store

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

class FlowStoreTest :
    FunSpec({
      test("get reads through to the store on a miss") {
        val store = InMemoryStore<String, String>()
        store.seed("k", "seeded", 1L)
        val consumer = flowStore(store, emptyFlow(), Caffeine.newBuilder(), backgroundScope)

        consumer.get("k") shouldBe "seeded"
        consumer.get("missing").shouldBeNull()
      }

      test("a delta older than a read-through load is dropped; a newer one is applied") {
        val store = InMemoryStore<String, String>()
        store.seed("k", "v5", 5L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)

        consumer.asFlow().test {
          inbound.subscriptionCount.first {
            it >= 1
          } // the store's collector has attached to inbound

          consumer.get("k") shouldBe "v5"

          inbound.emit(VersionedMapEvent.Upsert("k", "stale", 3L))
          awaitItem() // collector processed the delta (gated out)
          consumer.get("k") shouldBe "v5"

          inbound.emit(VersionedMapEvent.Upsert("k", "v9", 9L))
          awaitItem()
          consumer.get("k") shouldBe "v9"
        }
      }

      test(
          "a delta for an absent key is reflected, so a later read does not regress to a stale value"
      ) {
        // The owner committed v9 and published the delta, but the read path (a lagging replica,
        // modelled by leaving the backing store at v5) has not yet applied it. The consumer has
        // already seen v9 on its stream, so it must serve v9 and never regress to the replica's v5.
        val store = InMemoryStore<String, String>()
        store.seed("k", "v5", 5L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)

        consumer.asFlow().test {
          inbound.subscriptionCount.first { it >= 1 }

          // v9 reaches the stream before any read-through has populated the cache.
          inbound.emit(VersionedMapEvent.Upsert("k", "v9", 9L))
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v9", 9L)

          // The delta must have been reflected even though the key was absent, so get serves v9
          // without falling back to the stale replica read. (Currently reflectIfNewer only updates
          // resident keys, so the delta is dropped and get reads v5 — this assertion fails.)
          consumer.get("k") shouldBe "v9"
          store.loadCount.get() shouldBe 0 // the delta populated the cache; no read-through needed
        }
      }

      test("an equal-version delta after a read-through is gated out") {
        val store = InMemoryStore<String, String>()
        store.seed("k", "v2", 2L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)

        consumer.asFlow().test {
          inbound.subscriptionCount.first { it >= 1 }

          consumer.get("k") shouldBe "v2"

          inbound.emit(VersionedMapEvent.Upsert("k", "wrong", 2L))
          awaitItem()
          consumer.get("k") shouldBe "v2"
        }
      }

      test("a removal tombstones a resident entry so a later read cannot resurrect it") {
        val store = InMemoryStore<String, String>()
        store.seed("k", "v5", 5L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val consumer =
            FlowStoreImpl(store, FlowStoreCache(cache), inbound, backgroundScope, Dispatchers.IO)

        consumer.asFlow().test {
          inbound.subscriptionCount.first { it >= 1 }

          consumer.get("k") shouldBe "v5" // resident Live(v5, 5)

          inbound.emit(VersionedMapEvent.Removed("k", 6L))
          awaitItem()
          cache.getIfPresent("k") shouldBe Tombstone(6L)
          consumer.get("k").shouldBeNull() // tombstone short-circuits the read-through
        }
      }

      test("consumer converges to the owner's state through the delta stream") {
        val store = InMemoryStore<String, String>()
        val owner = mutableFlowStore(store, Caffeine.newBuilder(), txContext = inMemoryTxContext)
        fun commit(action: (InMemoryTx) -> Unit) = InMemoryTx().also { action(it) }.commit()
        val attached = MutableStateFlow(false)
        val ownerDeltas = owner.asFlow().onSubscription { attached.value = true }
        val consumer = flowStore(store, ownerDeltas, Caffeine.newBuilder(), backgroundScope)

        consumer.valueFlow("k").test {
          attached.first { it } // the consumer's collector has attached to the owner's stream
          awaitItem().shouldBeNull()

          commit { owner.put("k", "v1", it) }
          awaitItem() shouldBe "v1"

          commit { owner.put("k", "v2", it) }
          awaitItem() shouldBe "v2"

          commit { owner.remove("k", it) }
          awaitItem().shouldBeNull()
        }
      }

      test("valueFlow ignores deltas for other keys and stale versions") {
        val store = InMemoryStore<String, String>()
        store.seed("k", "v5", 5L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)

        consumer.valueFlow("k").test {
          awaitItem() shouldBe "v5" // seeded via read-through at version 5
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(VersionedMapEvent.Upsert("other", "x", 9L)) // different key -> ignored
          inbound.emit(VersionedMapEvent.Upsert("k", "stale", 3L)) // older version -> ignored
          inbound.emit(VersionedMapEvent.Upsert("k", "v9", 9L)) // newer -> emitted
          awaitItem() shouldBe "v9"
          expectNoEvents()
        }
      }

      test("the consumer's async view reads through and follows the stream") {
        val store = InMemoryStore<String, String>()
        store.seed("k", "seeded", 1L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)

        consumer.async.get("k") shouldBe "seeded" // miss -> dispatched read-through
        consumer.async.get("k") shouldBe "seeded" // hit -> served inline
        consumer.async.get("missing").shouldBeNull()

        consumer.async.valueFlow("k").test {
          awaitItem() shouldBe "seeded"
          inbound.subscriptionCount.first { it >= 1 }
          inbound.emit(VersionedMapEvent.Upsert("k", "v9", 9L))
          awaitItem() shouldBe "v9"
        }

        consumer.async.asFlow().replayCache shouldBe emptyList() // delegates to the delta stream
      }

      test("asFlow(query) emits the snapshot then follows newer deltas") {
        val store = InMemoryStore<String, String>()
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)
        val query = { mapOf("a" to Versioned("a1", 1L), "b" to Versioned("b2", 2L)) }

        consumer.asFlow(query).test {
          setOf(awaitItem(), awaitItem()) shouldBe
              setOf(
                  VersionedMapEvent.Upsert("a", "a1", 1L),
                  VersionedMapEvent.Upsert("b", "b2", 2L),
              )
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(VersionedMapEvent.Upsert("a", "a3", 3L))
          awaitItem() shouldBe VersionedMapEvent.Upsert("a", "a3", 3L)
        }
      }

      test("asFlow(query) gates a delta older-or-equal to the snapshot version") {
        val store = InMemoryStore<String, String>()
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)
        val query = { mapOf("k" to Versioned("v5", 5L)) }

        consumer.asFlow(query).test {
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v5", 5L)
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(VersionedMapEvent.Upsert("k", "stale", 5L)) // equal version -> gated
          inbound.emit(VersionedMapEvent.Upsert("k", "v9", 9L)) // newer -> emitted
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "v9", 9L)
          expectNoEvents()
        }
      }

      test("asFlow(query) with no predicate follows the whole store: new keys and removals") {
        val store = InMemoryStore<String, String>()
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)
        val query = { emptyMap<String, Versioned<String>>() }

        consumer.asFlow(query).test {
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(VersionedMapEvent.Upsert("new", "x", 1L)) // new key enters the view
          awaitItem() shouldBe VersionedMapEvent.Upsert("new", "x", 1L)

          inbound.emit(VersionedMapEvent.Removed("new", 2L)) // removal forwards
          awaitItem() shouldBe VersionedMapEvent.Removed("new", 2L)
        }
      }

      test("asFlow(query, predicate) ignores a non-matching upsert for an untracked key") {
        val store = InMemoryStore<String, String>()
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)
        val query = { emptyMap<String, Versioned<String>>() }
        val predicate = { _: String, v: String -> v.startsWith("keep") }

        consumer.asFlow(query, predicate).test {
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(
              VersionedMapEvent.Upsert("a", "drop-me", 1L)
          ) // untracked, no match -> ignored
          inbound.emit(VersionedMapEvent.Upsert("b", "keep-me", 2L)) // matches -> enters
          awaitItem() shouldBe VersionedMapEvent.Upsert("b", "keep-me", 2L)
          expectNoEvents()
        }
      }

      test("asFlow(query, predicate) emits Removed when an in-view item stops matching") {
        val store = InMemoryStore<String, String>()
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)
        val query = { mapOf("k" to Versioned("keep-1", 1L)) }
        val predicate = { _: String, v: String -> v.startsWith("keep") }

        consumer.asFlow(query, predicate).test {
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "keep-1", 1L)
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(VersionedMapEvent.Upsert("k", "gone-2", 2L)) // no longer matches -> leaves
          awaitItem() shouldBe VersionedMapEvent.Removed("k", 2L)
          expectNoEvents()
        }
      }

      test("asFlow(query, predicate) forwards a removal only for a key in the view") {
        val store = InMemoryStore<String, String>()
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val consumer = flowStore(store, inbound, Caffeine.newBuilder(), backgroundScope)
        val query = { mapOf("k" to Versioned("keep-1", 1L)) }
        val predicate = { _: String, v: String -> v.startsWith("keep") }

        consumer.asFlow(query, predicate).test {
          awaitItem() shouldBe VersionedMapEvent.Upsert("k", "keep-1", 1L)
          inbound.subscriptionCount.first { it >= 1 }

          inbound.emit(VersionedMapEvent.Removed("other", 2L)) // untracked -> ignored
          inbound.emit(VersionedMapEvent.Removed("k", 3L)) // in view -> forwarded
          awaitItem() shouldBe VersionedMapEvent.Removed("k", 3L)
          expectNoEvents()
        }
      }
    })
