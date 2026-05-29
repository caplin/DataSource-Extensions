package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class SimpleMapEventDeserializer :
    StdDeserializer<SimpleMapEvent<*, *>>(SimpleMapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SimpleMapEvent<*, *> {
    val node = ctxt.readTree(p)
    val type =
        node.get("type")?.asString()
            ?: throw DatabindException.from(p, "Missing type field for SimpleMapEvent")
    return when (type) {
      "populated" -> SimpleMapEvent.Populated
      "upsert" -> {
        val key =
            node.get("key")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing key field for upsert")
        val newValue =
            node.get("newValue")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing newValue field for upsert")
        SimpleMapEvent.EntryEvent.Upsert(key, newValue)
      }
      "removed" -> {
        val key =
            node.get("key")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing key field for removed")
        SimpleMapEvent.EntryEvent.Removed<Any, Any>(key)
      }
      else -> throw DatabindException.from(p, "Unknown SimpleMapEvent type: $type")
    }
  }
}
