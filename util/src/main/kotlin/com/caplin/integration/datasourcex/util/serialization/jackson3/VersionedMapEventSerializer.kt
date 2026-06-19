package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal class VersionedMapEventSerializer :
    StdSerializer<VersionedMapEvent<*, *>>(VersionedMapEvent::class.java) {
  override fun serialize(
      value: VersionedMapEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializationContext,
  ) {
    gen.writeStartObject()
    when (value) {
      is VersionedMapEvent.Upsert -> {
        gen.writeStringProperty("type", "upsert")
        gen.writeName("key")
        provider.writeValue(gen, value.key)
        gen.writeName("value")
        provider.writeValue(gen, value.value)
        gen.writeNumberProperty("version", value.version)
      }
      is VersionedMapEvent.Removed -> {
        gen.writeStringProperty("type", "removed")
        gen.writeName("key")
        provider.writeValue(gen, value.key)
        gen.writeNumberProperty("version", value.version)
      }
    }
    gen.writeEndObject()
  }
}
