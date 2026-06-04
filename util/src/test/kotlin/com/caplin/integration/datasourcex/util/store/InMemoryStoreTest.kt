package com.caplin.integration.datasourcex.util.store

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class InMemoryStoreTest :
    FunSpec({
      test("writes are loadable and deletes remove the entry") {
        val store = InMemoryStore<String, String>()
        val tx = inMemoryTxContext(InMemoryTx())

        store.write("a", "A", tx)
        store.load("a") shouldBe Versioned("A", 1L)

        store.delete("a", tx)
        store.load("a").shouldBeNull()
      }
    })
