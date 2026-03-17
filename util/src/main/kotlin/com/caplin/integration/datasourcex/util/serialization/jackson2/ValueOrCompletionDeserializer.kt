package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

internal class ValueOrCompletionDeserializer :
    StdDeserializer<ValueOrCompletion<*>>(ValueOrCompletion::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ValueOrCompletion<*> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for ValueOrCompletion")
    return when (type) {
      "value" -> {
        val value =
            node.get("value")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing value field for value type")
        ValueOrCompletion.Value(value)
      }
      "completion" -> {
        val error = node.get("error")?.let { if (it.isNull) null else it.asText() }
        ValueOrCompletion.Completion(error?.let { RuntimeException(it) })
      }
      else -> throw JsonMappingException.from(p, "Unknown ValueOrCompletion type: $type")
    }
  }
}
