package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class SimpleMapEventSerializer :
    StdSerializer<SimpleMapEvent<*, *>>(SimpleMapEvent::class.java) {
  override fun serialize(
      value: SimpleMapEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializerProvider,
  ) {
    gen.writeStartObject()
    when (value) {
      is SimpleMapEvent.Populated -> {
        gen.writeStringField("type", "populated")
      }
      is SimpleMapEvent.EntryEvent.Upsert -> {
        gen.writeStringField("type", "upsert")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeFieldName("newValue")
        provider.defaultSerializeValue(value.newValue, gen)
      }
      is SimpleMapEvent.EntryEvent.Removed -> {
        gen.writeStringField("type", "removed")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
      }
    }
    gen.writeEndObject()
  }
}
