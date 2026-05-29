package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.SetEvent
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal class SetEventSerializer : StdSerializer<SetEvent<*>>(SetEvent::class.java) {
  override fun serialize(
      value: SetEvent<*>,
      gen: JsonGenerator,
      provider: SerializationContext,
  ) {
    gen.writeStartObject()
    when (value) {
      is SetEvent.Populated -> {
        gen.writeStringProperty("type", "populated")
      }
      is SetEvent.EntryEvent.Insert -> {
        gen.writeStringProperty("type", "insert")
        gen.writeName("value")
        provider.writeValue(gen, value.value)
      }
      is SetEvent.EntryEvent.Removed -> {
        gen.writeStringProperty("type", "removed")
        gen.writeName("value")
        provider.writeValue(gen, value.value)
      }
    }
    gen.writeEndObject()
  }
}
