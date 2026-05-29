package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.SetEvent
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class SetEventDeserializer : StdDeserializer<SetEvent<*>>(SetEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SetEvent<*> {
    val node = ctxt.readTree(p)
    val type =
        node.get("type")?.asString()
            ?: throw DatabindException.from(p, "Missing type field for SetEvent")
    return when (type) {
      "populated" -> SetEvent.Populated
      "insert" -> {
        val value =
            node.get("value")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing value field for insert")
        SetEvent.EntryEvent.Insert(value)
      }
      "removed" -> {
        val value =
            node.get("value")?.let { ctxt.readTreeAsValue(it, Any::class.java) }
                ?: throw DatabindException.from(p, "Missing value field for removed")
        SetEvent.EntryEvent.Removed(value)
      }
      else -> throw DatabindException.from(p, "Unknown SetEvent type: $type")
    }
  }
}
