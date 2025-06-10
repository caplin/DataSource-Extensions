@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay

class TimeoutKtTest :
    FunSpec({
      test("withTimeout throws TimeoutException") {
        try {
          withTimeout(10) { delay(11) }
        } catch (t: Throwable) {
          t.shouldBeInstanceOf<TimeoutException>()
        }
      }
    })
