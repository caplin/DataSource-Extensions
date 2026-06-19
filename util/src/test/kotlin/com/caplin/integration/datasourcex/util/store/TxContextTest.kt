package com.caplin.integration.datasourcex.util.store

import io.kotest.core.spec.style.FunSpec

class TxContextTest :
    FunSpec({
      test("onRollback defaults to a no-op") {
        val ctx =
            object : TxContext<Unit> {
              override val transaction = Unit

              override fun onCommitEnd(action: () -> Unit) {}
            }

        ctx.onRollback { error("must not run") } // default no-op: registers nothing, never fires
      }
    })
