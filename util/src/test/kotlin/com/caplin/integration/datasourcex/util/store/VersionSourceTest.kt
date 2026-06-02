package com.caplin.integration.datasourcex.util.store

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicLong

class VersionSourceTest :
    FunSpec({
      test("CountingVersionSource is strictly increasing from start + 1") {
        val source = CountingVersionSource(start = 100L)

        List(5) { source.next() } shouldBe listOf(101L, 102L, 103L, 104L, 105L)
      }

      test("TimestampVersionSource bumps past a stalled clock") {
        val source = TimestampVersionSource(nowMicros = { 1_000L })

        List(4) { source.next() } shouldBe listOf(1_000L, 1_001L, 1_002L, 1_003L)
      }

      test("TimestampVersionSource stays strictly increasing under a regressing clock") {
        val clock = AtomicLong(10_000L)
        val source = TimestampVersionSource(nowMicros = { clock.getAndAdd(-100L) })

        val seq = List(50) { source.next() }

        seq.zipWithNext().forEach { (a, b) -> b shouldBeGreaterThan a }
      }
    })
