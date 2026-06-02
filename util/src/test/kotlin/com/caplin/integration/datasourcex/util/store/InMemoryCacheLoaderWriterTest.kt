package com.caplin.integration.datasourcex.util.store

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

class InMemoryCacheLoaderWriterTest :
    FunSpec({
      test("writes are loadable and deletes remove the entry") {
        val store = InMemoryCacheLoaderWriter<String, String>()
        val tx = AutoCommitTxContext(Unit)

        store.write("a", "A", tx)
        store.write("b", "B", tx)

        store.load("a") shouldBe Versioned("A", 1L)
        store.loadAll(listOf("a", "b", "missing")) shouldBe
            mapOf("a" to Versioned("A", 1L), "b" to Versioned("B", 2L))
        store.loadAllKeys().toList().toSet() shouldBe setOf("a", "b")

        store.delete("a", tx)
        store.load("a").shouldBeNull()
      }

      test("AutoCommitTxContext fires commit actions and skips rollback actions") {
        val tx = AutoCommitTxContext(Unit)
        val log = mutableListOf<String>()
        tx.onCommitEnd { log += "commit" }
        tx.onRollback { log += "rollback" }

        tx.commit()

        log shouldBe listOf("commit")
      }
    })
