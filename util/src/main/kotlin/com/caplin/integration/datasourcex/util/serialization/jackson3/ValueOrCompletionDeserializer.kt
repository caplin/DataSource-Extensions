package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer

internal class ValueOrCompletionDeserializer :
    StdDeserializer<ValueOrCompletion<*>>(ValueOrCompletion::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ValueOrCompletion<*> {
    val node = ctxt.readTree(p)
    val type =
        node.get("type")?.asString()
            ?: throw DatabindException.from(p, "Missing type field for ValueOrCompletion")
    return when (type) {
      "value" -> {
        val valueNode =
            node.get("value")
                ?: throw DatabindException.from(p, "Missing value field for value type")
        val value = if (valueNode.isNull) null else ctxt.readTreeAsValue(valueNode, Any::class.java)
        ValueOrCompletion.Value(value)
      }
      "completion" -> {
        val error = node.get("error")?.let { if (it.isNull) null else it.asString() }
        ValueOrCompletion.Completion(error?.let { RuntimeException(it) })
      }
      else -> throw DatabindException.from(p, "Unknown ValueOrCompletion type: $type")
    }
  }
}
