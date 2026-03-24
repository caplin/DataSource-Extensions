package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

internal class MapEventDeserializer : StdDeserializer<MapEvent<*, *>>(MapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MapEvent<*, *> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for MapEvent")
    return when (type) {
      "populated" -> MapEvent.Populated
      "upsert" -> {
        val key =
            node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing key field for upsert")
        val oldValueNode = node.get("oldValue")
        val oldValue =
            if (oldValueNode == null || oldValueNode.isNull) null
            else p.codec.treeToValue(oldValueNode, Any::class.java)
        val newValue =
            node.get("newValue")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing newValue field for upsert")
        MapEvent.EntryEvent.Upsert(key, oldValue, newValue)
      }
      "removed" -> {
        val key =
            node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing key field for removed")
        val oldValue =
            node.get("oldValue")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing oldValue field for removed")
        MapEvent.EntryEvent.Removed(key, oldValue)
      }
      else -> throw JsonMappingException.from(p, "Unknown MapEvent type: $type")
    }
  }
}
