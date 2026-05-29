package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.MapEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.jacksonMapperBuilder

class Jackson3JsonHandlerTest :
    FunSpec({
      val handler = Jackson3JsonHandler(jacksonMapperBuilder().addDataSourceModule().build())

      val upsert: MapEvent<String, String> = MapEvent.EntryEvent.Upsert("k", "old", "new")
      val populated: MapEvent<String, String> = MapEvent.Populated

      test("toJsonTree / toObject round-trips a value") {
        val tree = handler.toJsonTree(upsert)
        handler.toObject(tree, MapEvent::class.java) shouldBe upsert
      }

      test("parse / format round-trips a tree") {
        val tree = handler.toJsonTree(upsert)
        handler.parse(handler.format(tree)) shouldBe tree
      }

      test("diff / patch round-trips source to target") {
        val source = handler.toJsonTree(populated)
        val target = handler.toJsonTree(upsert)
        handler.patch(source, handler.diff(source, target)) shouldBe target
      }
    })
