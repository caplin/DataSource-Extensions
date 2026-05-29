package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.MapEvent
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal class MapEventSerializer : StdSerializer<MapEvent<*, *>>(MapEvent::class.java) {
  override fun serialize(
      value: MapEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializationContext,
  ) {
    gen.writeStartObject()
    when (value) {
      is MapEvent.Populated -> {
        gen.writeStringProperty("type", "populated")
      }
      is MapEvent.EntryEvent.Upsert -> {
        gen.writeStringProperty("type", "upsert")
        gen.writeName("key")
        provider.writeValue(gen, value.key)
        gen.writeName("oldValue")
        provider.writeValue(gen, value.oldValue)
        gen.writeName("newValue")
        provider.writeValue(gen, value.newValue)
      }
      is MapEvent.EntryEvent.Removed -> {
        gen.writeStringProperty("type", "removed")
        gen.writeName("key")
        provider.writeValue(gen, value.key)
        gen.writeName("oldValue")
        provider.writeValue(gen, value.oldValue)
      }
    }
    gen.writeEndObject()
  }
}
