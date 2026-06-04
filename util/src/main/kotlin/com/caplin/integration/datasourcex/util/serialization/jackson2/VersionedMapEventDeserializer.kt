package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Keys and values round-trip as JSON-native types only: a String key returns a String, but a
 * numeric key comes back as Integer/Long and a structured value as a Map, not its original type.
 * Intended for String (or otherwise JSON-native) keys and values.
 */
internal class VersionedMapEventDeserializer :
    StdDeserializer<VersionedMapEvent<*, *>>(VersionedMapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VersionedMapEvent<*, *> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for VersionedMapEvent")
    val key =
        node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
            ?: throw JsonMappingException.from(p, "Missing key field for VersionedMapEvent")
    val version =
        node.get("version")?.takeIf { it.isIntegralNumber }?.asLong()
            ?: throw JsonMappingException.from(
                p,
                "Missing or non-integral version field for VersionedMapEvent",
            )
    return when (type) {
      "upsert" -> {
        val value =
            node.get("value")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing value field for upsert")
        VersionedMapEvent.Upsert(key, value, version)
      }
      "removed" -> VersionedMapEvent.Removed(key, version)
      else -> throw JsonMappingException.from(p, "Unknown VersionedMapEvent type: $type")
    }
  }
}
