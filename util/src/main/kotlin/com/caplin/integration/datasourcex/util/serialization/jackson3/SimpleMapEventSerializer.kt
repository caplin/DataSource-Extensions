package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal class SimpleMapEventSerializer :
    StdSerializer<SimpleMapEvent<*, *>>(SimpleMapEvent::class.java) {
  override fun serialize(
      value: SimpleMapEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializationContext,
  ) {
    gen.writeStartObject()
    when (value) {
      is SimpleMapEvent.Populated -> {
        gen.writeStringProperty("type", "populated")
      }
      is SimpleMapEvent.EntryEvent.Upsert -> {
        gen.writeStringProperty("type", "upsert")
        gen.writeName("key")
        provider.writeValue(gen, value.key)
        gen.writeName("newValue")
        provider.writeValue(gen, value.newValue)
      }
      is SimpleMapEvent.EntryEvent.Removed -> {
        gen.writeStringProperty("type", "removed")
        gen.writeName("key")
        provider.writeValue(gen, value.key)
      }
    }
    gen.writeEndObject()
  }
}
