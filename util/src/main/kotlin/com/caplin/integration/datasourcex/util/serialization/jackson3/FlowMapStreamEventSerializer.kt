package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

internal class FlowMapStreamEventSerializer :
    StdSerializer<FlowMapStreamEvent<*, *>>(FlowMapStreamEvent::class.java) {
  override fun serialize(
      value: FlowMapStreamEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializationContext,
  ) {
    gen.writeStartObject()
    when (value) {
      is FlowMapStreamEvent.InitialState -> {
        gen.writeStringProperty("type", "initial")
        gen.writeName("map")
        provider.writeValue(gen, value.map)
      }
      is FlowMapStreamEvent.EventUpdate -> {
        gen.writeStringProperty("type", "update")
        gen.writeName("event")
        provider.writeValue(gen, value.event)
      }
      is FlowMapStreamEvent.Cleared -> {
        gen.writeStringProperty("type", "cleared")
      }
    }
    gen.writeEndObject()
  }
}
