package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class VersionedMapEventSerializer :
    StdSerializer<VersionedMapEvent<*, *>>(VersionedMapEvent::class.java) {
  override fun serialize(
      value: VersionedMapEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializerProvider,
  ) {
    gen.writeStartObject()
    when (value) {
      is VersionedMapEvent.Upsert -> {
        gen.writeStringField("type", "upsert")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeFieldName("value")
        provider.defaultSerializeValue(value.value, gen)
        gen.writeNumberField("version", value.version)
      }
      is VersionedMapEvent.Removed -> {
        gen.writeStringField("type", "removed")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeNumberField("version", value.version)
      }
    }
    gen.writeEndObject()
  }
}
