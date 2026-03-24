package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.SetEvent
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class SetEventSerializer : StdSerializer<SetEvent<*>>(SetEvent::class.java) {
  override fun serialize(
      value: SetEvent<*>,
      gen: JsonGenerator,
      provider: SerializerProvider,
  ) {
    gen.writeStartObject()
    when (value) {
      is SetEvent.Populated -> {
        gen.writeStringField("type", "populated")
      }
      is SetEvent.EntryEvent.Insert -> {
        gen.writeStringField("type", "insert")
        gen.writeFieldName("value")
        provider.defaultSerializeValue(value.value, gen)
      }
      is SetEvent.EntryEvent.Removed -> {
        gen.writeStringField("type", "removed")
        gen.writeFieldName("value")
        provider.defaultSerializeValue(value.value, gen)
      }
    }
    gen.writeEndObject()
  }
}
