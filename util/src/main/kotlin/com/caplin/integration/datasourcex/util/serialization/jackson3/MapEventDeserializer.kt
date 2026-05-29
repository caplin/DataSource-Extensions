package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.MapEvent
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class MapEventDeserializer : StdDeserializer<MapEvent<*, *>>(MapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MapEvent<*, *> {
    val node = ctxt.readTree(p)
    val type =
        node.get("type")?.asString()
            ?: throw DatabindException.from(p, "Missing type field for MapEvent")
    return when (type) {
      "populated" -> MapEvent.Populated
      "upsert" -> {
        val key =
            node.get("key")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing key field for upsert")
        val oldValueNode = node.get("oldValue")
        val oldValue =
            if (oldValueNode == null || oldValueNode.isNull) null
            else ctxt.readTreeAsValue(oldValueNode, Any::class.java)
        val newValue =
            node.get("newValue")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing newValue field for upsert")
        MapEvent.EntryEvent.Upsert(key, oldValue, newValue)
      }
      "removed" -> {
        val key =
            node.get("key")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing key field for removed")
        val oldValue =
            node.get("oldValue")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing oldValue field for removed")
        MapEvent.EntryEvent.Removed(key, oldValue)
      }
      else -> throw DatabindException.from(p, "Unknown MapEvent type: $type")
    }
  }
}
