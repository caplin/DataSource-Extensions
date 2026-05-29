package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal class ValueOrCompletionSerializer :
    StdSerializer<ValueOrCompletion<*>>(ValueOrCompletion::class.java) {
  override fun serialize(
      value: ValueOrCompletion<*>,
      gen: JsonGenerator,
      provider: SerializationContext,
  ) {
    gen.writeStartObject()
    when (value) {
      is ValueOrCompletion.Value -> {
        gen.writeStringProperty("type", "value")
        gen.writeName("value")
        provider.writeValue(gen, value.value)
      }
      is ValueOrCompletion.Completion -> {
        gen.writeStringProperty("type", "completion")
        if (value.throwable != null) {
          gen.writeStringProperty("error", value.throwable.message ?: value.throwable.toString())
        } else {
          gen.writeNullProperty("error")
        }
      }
    }
    gen.writeEndObject()
  }
}
