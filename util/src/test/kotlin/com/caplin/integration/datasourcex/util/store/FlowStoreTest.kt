package com.caplin.integration.datasourcex.util.store

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.cache.buildSuspending
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

class FlowStoreTest :
    FunSpec({
      test("get reads through to the store on a miss") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.write("k", "seeded", 1L, AutoCommitTxContext(Unit))
        val consumer =
            flowStore(
                store,
                emptyFlow(),
                Caffeine.newBuilder().buildSuspending(backgroundScope),
                backgroundScope,
            )

        consumer.get("k") shouldBe "seeded"
        consumer.get("missing").shouldBeNull()
      }

      test("a delta older than a read-through load is dropped; a newer one is applied") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.write("k", "v5", 5L, AutoCommitTxContext(Unit))
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val cache =
            Caffeine.newBuilder().buildSuspending<String, Versioned<String>>(backgroundScope)
        val consumer = flowStore(store, inbound, cache, backgroundScope)
        delay(1.milliseconds)

        consumer.get("k") shouldBe "v5"

        inbound.emit(VersionedMapEvent.Upsert("k", "stale", 3L))
        delay(1.milliseconds)
        consumer.get("k") shouldBe "v5"

        inbound.emit(VersionedMapEvent.Upsert("k", "v9", 9L))
        delay(1.milliseconds)
        consumer.get("k") shouldBe "v9"
      }

      test("an equal-version delta after a read-through is gated out") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        store.write("k", "v2", 2L, AutoCommitTxContext(Unit))
        val inbound = MutableSharedFlow<VersionedMapEvent<String, String>>(extraBufferCapacity = 16)
        val cache =
            Caffeine.newBuilder().buildSuspending<String, Versioned<String>>(backgroundScope)
        val consumer = flowStore(store, inbound, cache, backgroundScope)
        delay(1.milliseconds)

        consumer.get("k") shouldBe "v2"

        inbound.emit(VersionedMapEvent.Upsert("k", "wrong", 2L))
        delay(1.milliseconds)
        consumer.get("k") shouldBe "v2"
      }

      test("consumer converges to the owner's state through the delta stream") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        val owner =
            mutableFlowStore(
                store,
                inMemoryTransactionRunner,
                Caffeine.newBuilder().buildSuspending(backgroundScope),
            )
        val consumer =
            flowStore(
                store,
                owner.asFlow(),
                Caffeine.newBuilder().buildSuspending(backgroundScope),
                backgroundScope,
            )

        consumer.valueFlow("k").test {
          delay(1.milliseconds)
          awaitItem().shouldBeNull()

          owner.put("k", "v1")
          awaitItem() shouldBe "v1"

          owner.put("k", "v2")
          awaitItem() shouldBe "v2"

          owner.remove("k")
          awaitItem().shouldBeNull()
        }
      }
    })
