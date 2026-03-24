package com.caplin.integration.datasourcex.util.flow

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class CommonKtTest :
    FunSpec({ test("UNSET is a valid non-null instance") { UNSET.shouldNotBeNull() } })
