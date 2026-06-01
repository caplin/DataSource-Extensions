package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class FlowMapStreamEventDeserializer :
    StdDeserializer<FlowMapStreamEvent<*, *>>(FlowMapStreamEvent::class.java) {
  @Suppress("UNCHECKED_CAST")
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FlowMapStreamEvent<*, *> {
    val node = ctxt.readTree(p)
    val type =
        node.get("type")?.asString()
            ?: throw DatabindException.from(p, "Missing type field for FlowMapStreamEvent")
    return when (type) {
      "initial" -> {
        val mapNode =
            node.get("map") ?: throw DatabindException.from(p, "Missing map field for initial")
        val map = ctxt.readTreeAsValue(mapNode, Map::class.java) as Map<Any, Any>
        FlowMapStreamEvent.InitialState(map)
      }
      "update" -> {
        val eventNode =
            node.get("event") ?: throw DatabindException.from(p, "Missing event field for update")
        val event =
            ctxt.readTreeAsValue(eventNode, MapEvent.EntryEvent::class.java)
                as MapEvent.EntryEvent<Any, Any>
        FlowMapStreamEvent.EventUpdate(event)
      }
      "cleared" -> FlowMapStreamEvent.Cleared
      else -> throw DatabindException.from(p, "Unknown type: $type")
    }
  }
}
