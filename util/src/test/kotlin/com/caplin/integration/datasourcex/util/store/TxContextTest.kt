package com.caplin.integration.datasourcex.util.store

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TxContextTest :
    FunSpec({
      test("commit fires actions once and rejects reuse") {
        var commits = 0
        val tx = AutoCommitTxContext(Unit)
        tx.onCommitEnd { commits++ }

        tx.commit()
        commits shouldBe 1
        shouldThrow<IllegalStateException> { tx.commit() }
        commits shouldBe 1
      }

      test("rollback is a no-op once committed and does not fire rollback actions") {
        var rollbacks = 0
        val tx = AutoCommitTxContext(Unit)
        tx.onRollback { rollbacks++ }

        tx.commit()
        tx.rollback()
        rollbacks shouldBe 0
      }

      test("commit runs every action even if one throws, then rethrows") {
        val log = mutableListOf<String>()
        val tx = AutoCommitTxContext(Unit)
        tx.onCommitEnd { log += "a" }
        tx.onCommitEnd { throw RuntimeException("boom") }
        tx.onCommitEnd { log += "c" }

        shouldThrow<RuntimeException> { tx.commit() }
        log shouldBe listOf("a", "c")
      }
    })
