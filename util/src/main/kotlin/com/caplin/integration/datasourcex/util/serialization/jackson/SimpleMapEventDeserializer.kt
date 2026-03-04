package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

internal class SimpleMapEventDeserializer :
    StdDeserializer<SimpleMapEvent<*, *>>(SimpleMapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SimpleMapEvent<*, *> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for SimpleMapEvent")
    return when (type) {
      "populated" -> SimpleMapEvent.Populated
      "upsert" -> {
        val key =
            node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing key field for upsert")
        val newValue =
            node.get("newValue")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing newValue field for upsert")
        SimpleMapEvent.EntryEvent.Upsert(key, newValue)
      }
      "removed" -> {
        val key =
            node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing key field for removed")
        SimpleMapEvent.EntryEvent.Removed<Any, Any>(key)
      }
      else -> throw JsonMappingException.from(p, "Unknown SimpleMapEvent type: $type")
    }
  }
}
