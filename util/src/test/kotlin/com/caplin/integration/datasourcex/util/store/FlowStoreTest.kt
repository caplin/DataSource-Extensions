package com.caplin.integration.datasourcex.util.store

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

class FlowStoreTest :
    FunSpec({
      test("get reads through to the store on a miss") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.seed("k", "seeded", 1L)
        val consumer =
            flowStore(
                store,
                emptyFlow(),
                Caffeine.newBuilder().build(),
                backgroundScope,
            )

        consumer.get("k") shouldBe "seeded"
        consumer.get("missing").shouldBeNull()
      }

      test("a delta older than a read-through load is dropped; a newer one is applied") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.seed("k", "v5", 5L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val consumer = flowStore(store, inbound, cache, backgroundScope)

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

      test("an equal-version delta after a read-through is gated out") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.seed("k", "v2", 2L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val consumer = flowStore(store, inbound, cache, backgroundScope)

        consumer.asFlow().test {
          inbound.subscriptionCount.first { it >= 1 }

          consumer.get("k") shouldBe "v2"

          inbound.emit(VersionedMapEvent.Upsert("k", "wrong", 2L))
          awaitItem()
          consumer.get("k") shouldBe "v2"
        }
      }

      test("a removal tombstones a resident entry so a later read cannot resurrect it") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.seed("k", "v5", 5L)
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val cache = Caffeine.newBuilder().build<String, CacheEntry<String>?>()
        val consumer = flowStore(store, inbound, cache, backgroundScope)

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
        val store = InMemoryCacheLoaderWriter<String, String>()
        val owner =
            mutableFlowStore(
                store,
                Caffeine.newBuilder().build(),
                txContext = inMemoryTxContext,
            )
        fun commit(action: (InMemoryTx) -> Unit) = InMemoryTx().also { action(it) }.commit()
        val attached = MutableStateFlow(false)
        val ownerDeltas = owner.asFlow().onSubscription { attached.value = true }
        val consumer =
            flowStore(
                store,
                ownerDeltas,
                Caffeine.newBuilder().build(),
                backgroundScope,
            )

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
    })
