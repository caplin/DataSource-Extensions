package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class Jackson2JsonHandlerTest :
    FunSpec({
      val handler = Jackson2JsonHandler(jacksonObjectMapper().registerDataSourceModule())

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
