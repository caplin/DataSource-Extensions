package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class VersionedMapEventDeserializer :
    StdDeserializer<VersionedMapEvent<*, *>>(VersionedMapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): VersionedMapEvent<*, *> {
    val node = ctxt.readTree(p)
    val type =
        node.get("type")?.asString()
            ?: throw DatabindException.from(p, "Missing type field for VersionedMapEvent")
    val key =
        node.get("key")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
            ?: throw DatabindException.from(p, "Missing key field for VersionedMapEvent")
    val version =
        node.get("version")?.asLong()
            ?: throw DatabindException.from(p, "Missing version field for VersionedMapEvent")
    return when (type) {
      "upsert" -> {
        val value =
            node.get("value")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing value field for upsert")
        VersionedMapEvent.Upsert(key, value, version)
      }
      "removed" -> VersionedMapEvent.Removed<Any>(key, version)
      else -> throw DatabindException.from(p, "Unknown VersionedMapEvent type: $type")
    }
  }
}
