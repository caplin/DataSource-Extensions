package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.SetEvent
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

internal class SetEventDeserializer : StdDeserializer<SetEvent<*>>(SetEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SetEvent<*> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for SetEvent")
    return when (type) {
      "populated" -> SetEvent.Populated
      "insert" -> {
        val value =
            node.get("value")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing value field for insert")
        SetEvent.EntryEvent.Insert(value)
      }
      "removed" -> {
        val value =
            node.get("value")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing value field for removed")
        SetEvent.EntryEvent.Removed(value)
      }
      else -> throw JsonMappingException.from(p, "Unknown SetEvent type: $type")
    }
  }
}
