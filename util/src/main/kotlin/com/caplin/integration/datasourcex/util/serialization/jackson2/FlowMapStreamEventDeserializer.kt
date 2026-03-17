package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

internal class FlowMapStreamEventDeserializer :
    StdDeserializer<FlowMapStreamEvent<*, *>>(FlowMapStreamEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FlowMapStreamEvent<*, *> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for FlowMapStreamEvent")
    return when (type) {
      "initial" -> {
        val mapNode =
            node.get("map") ?: throw JsonMappingException.from(p, "Missing map field for initial")
        val map = p.codec.treeToValue(mapNode, Map::class.java) as Map<Any, Any>
        FlowMapStreamEvent.InitialState(map)
      }
      "update" -> {
        val eventNode =
            node.get("event")
                ?: throw JsonMappingException.from(p, "Missing event field for update")
        val event =
            p.codec.treeToValue(eventNode, MapEvent.EntryEvent::class.java)
                as MapEvent.EntryEvent<Any, Any>
        FlowMapStreamEvent.EventUpdate(event)
      }
      "cleared" -> FlowMapStreamEvent.Cleared
      else -> throw JsonMappingException.from(p, "Unknown type: $type")
    }
  }
}
