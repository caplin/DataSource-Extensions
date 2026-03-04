package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class MapEventSerializer : StdSerializer<MapEvent<*, *>>(MapEvent::class.java) {
  override fun serialize(value: MapEvent<*, *>, gen: JsonGenerator, provider: SerializerProvider) {
    gen.writeStartObject()
    when (value) {
      is MapEvent.Populated -> {
        gen.writeStringField("type", "populated")
      }
      is MapEvent.EntryEvent.Upsert -> {
        gen.writeStringField("type", "upsert")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeFieldName("oldValue")
        provider.defaultSerializeValue(value.oldValue, gen)
        gen.writeFieldName("newValue")
        provider.defaultSerializeValue(value.newValue, gen)
      }
      is MapEvent.EntryEvent.Removed -> {
        gen.writeStringField("type", "removed")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeFieldName("oldValue")
        provider.defaultSerializeValue(value.oldValue, gen)
      }
    }
    gen.writeEndObject()
  }
}
