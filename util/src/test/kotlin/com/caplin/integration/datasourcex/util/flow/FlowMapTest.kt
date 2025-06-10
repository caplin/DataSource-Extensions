@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex

class FlowMapTest :
    FunSpec({
      test("FlowMap asFlow") {
        val map = mutableFlowMapOf("1" to "A", "2" to "B")

        map.asFlow().test {
          awaitItem() shouldBeEqual Upsert("1", null, "A")
          awaitItem() shouldBeEqual Upsert("2", null, "B")
          awaitItem() shouldBeEqual Populated
          map.put("3", "C")
          awaitItem() shouldBeEqual Upsert("3", null, "C")
          map.put("2", "X")
          awaitItem() shouldBeEqual Upsert("2", "B", "X")
          map.remove("1")
          awaitItem() shouldBeEqual Removed("1", "A")
          map.put("2", "Z")
          awaitItem() shouldBeEqual Upsert("2", "X", "Z")
          map.put("4", "G")
          awaitItem() shouldBeEqual Upsert("4", null, "G")
        }
      }

      test("FlowMap asFlow with predicate") {
        val map = mutableFlowMapOf("1" to "AL", "2" to "B")

        map.asFlow(predicate = { _, value -> value.contains("L") }).test {
          awaitItem() shouldBeEqual Upsert("1", null, "AL")
          awaitItem() shouldBeEqual Populated
          map.put("3", "CL")
          awaitItem() shouldBeEqual Upsert("3", null, "CL")
          map.put("2", "BL")
          awaitItem() shouldBeEqual Upsert("2", null, "BL")
          map.remove("1")
          awaitItem() shouldBeEqual Removed("1", "AL")
          map.put("2", "ZL")
          awaitItem() shouldBeEqual Upsert("2", "BL", "ZL")
          map.put("4", "GL")
          awaitItem() shouldBeEqual Upsert("4", null, "GL")
          map.put("5", "M")
          expectNoEvents()
          map.put("2", "Z")
          awaitItem() shouldBeEqual Removed("2", "ZL")
          map.put("4", "TL")
          awaitItem() shouldBeEqual Upsert("4", "GL", "TL")
        }
      }

      test("FlowMap running fold to map") {
        val map = mutableFlowMapOf("1" to "AL", "3" to "LL", "2" to "B")

        map.asFlow(predicate = { _, value -> value.contains("L") }).runningFoldToMap().test {
          awaitItem() shouldContainExactly mapOf("1" to "AL", "3" to "LL")

          map.put("3", "CL")
          awaitItem() shouldContainExactly mapOf("1" to "AL", "3" to "CL")

          map.put("2", "BL")
          awaitItem() shouldContainExactly mapOf("1" to "AL", "3" to "CL", "2" to "BL")

          map.remove("1")
          awaitItem() shouldContainExactly mapOf("3" to "CL", "2" to "BL")

          map.put("2", "ZL")
          awaitItem() shouldContainExactly mapOf("3" to "CL", "2" to "ZL")
          map.put("4", "GL")
          awaitItem() shouldContainExactly mapOf("3" to "CL", "2" to "ZL", "4" to "GL")
          map.put("5", "M")
          expectNoEvents()

          map.put("2", "Z")
          awaitItem() shouldContainExactly mapOf("3" to "CL", "4" to "GL")

          map.put("4", "TL")
          awaitItem() shouldContainExactly mapOf("3" to "CL", "4" to "TL")
        }
      }

      test("FlowMap running fold to empty map") {
        val map = mutableFlowMapOf<String, String>()

        map.asFlow().runningFoldToMap().test { awaitItem() shouldContainExactly mapOf() }
      }

      test("FlowMap running fold with partials to empty map") {
        val map = mutableFlowMapOf<String, String>()

        map.asFlow().runningFoldToMap(true).test { awaitItem() shouldContainExactly mapOf() }
      }

      test("FlowMap running fold to map with partials") {
        val map = mutableFlowMapOf("1" to "AL", "2" to "B")

        map.asFlow().runningFoldToMap(true).test {
          awaitItem() shouldContainExactly mapOf()
          awaitItem() shouldContainExactly mapOf("1" to "AL")
          awaitItem() shouldContainExactly mapOf("1" to "AL", "2" to "B")

          map.put("3", "CL")
          awaitItem() shouldContainExactly mapOf("1" to "AL", "2" to "B", "3" to "CL")
        }
      }

      test("FlowMap slow collector").config(invocations = 100) {
        turbineScope {
          val map = mutableFlowMapOf("A" to 0)

          val lock = Mutex(false)

          val turbine = map.asFlow().onEach { lock.lock() }.testIn(backgroundScope)

          turbine.awaitItem() shouldBeEqual Upsert("A", null, 0)
          lock.unlock()
          turbine.awaitItem() shouldBeEqual Populated

          for (i in 1..100) {
            map.put("A", i)
          }

          turbine.expectNoEvents()

          for (i in 1..100) {
            lock.unlock()
            turbine.awaitItem() shouldBeEqual Upsert("A", i - 1, i)
          }
        }
      }

      test("FlowMap valueFlow") {
        val map = mutableFlowMapOf("1" to "A")

        map.valueFlow("1").test {
          awaitItem().shouldNotBeNull() shouldBeEqual "A"
          map.put("1", "B")
          awaitItem().shouldNotBeNull() shouldBeEqual "B"
          map.remove("1")
          awaitItem().shouldBeNull()
        }
      }

      test("FlowMap filtered to other FlowMap") {
        val primaryMap = mutableFlowMapOf("1" to "A", "2" to "Ax")

        val filteredMap =
            primaryMap.asFlow { _, value -> value.contains("x") }.toFlowMapIn(backgroundScope)

        filteredMap["1"] shouldBe null
        filteredMap["2"].shouldNotBeNull() shouldBeEqual "Ax"

        filteredMap.asFlow().test {
          awaitItem() shouldBeEqual Upsert("2", null, "Ax")
          awaitItem() shouldBeEqual Populated

          primaryMap["3"] = "B"
          primaryMap["4"] = "Bx"
          awaitItem() shouldBeEqual Upsert("4", null, "Bx")

          primaryMap.remove("4")
          awaitItem() shouldBeEqual Removed("4", "Bx")

          primaryMap["2"] = "A"
          awaitItem() shouldBeEqual Removed("2", "Ax")

          primaryMap["2"] = "Ax"
          awaitItem() shouldBeEqual Upsert("2", null, "Ax")
          primaryMap["2"] = "Axx"
          awaitItem() shouldBeEqual Upsert("2", "Ax", "Axx")
        }
      }
    })
