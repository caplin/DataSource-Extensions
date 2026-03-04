package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class ValueOrCompletionSerializer :
    StdSerializer<ValueOrCompletion<*>>(ValueOrCompletion::class.java) {
  override fun serialize(
      value: ValueOrCompletion<*>,
      gen: JsonGenerator,
      provider: SerializerProvider,
  ) {
    gen.writeStartObject()
    when (value) {
      is ValueOrCompletion.Value -> {
        gen.writeStringField("type", "value")
        gen.writeFieldName("value")
        provider.defaultSerializeValue(value.value, gen)
      }
      is ValueOrCompletion.Completion -> {
        gen.writeStringField("type", "completion")
        if (value.throwable != null) {
          gen.writeStringField("error", value.throwable.message ?: value.throwable.toString())
        } else {
          gen.writeNullField("error")
        }
      }
    }
    gen.writeEndObject()
  }
}
