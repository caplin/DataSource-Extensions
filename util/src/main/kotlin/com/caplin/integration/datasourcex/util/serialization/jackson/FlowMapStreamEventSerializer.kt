package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal class FlowMapStreamEventSerializer :
    StdSerializer<FlowMapStreamEvent<*, *>>(FlowMapStreamEvent::class.java) {
  override fun serialize(
      value: FlowMapStreamEvent<*, *>,
      gen: JsonGenerator,
      provider: SerializerProvider,
  ) {
    gen.writeStartObject()
    when (value) {
      is FlowMapStreamEvent.InitialState -> {
        gen.writeStringField("type", "initial")
        gen.writeFieldName("map")
        provider.defaultSerializeValue(value.map, gen)
      }
      is FlowMapStreamEvent.EventUpdate -> {
        gen.writeStringField("type", "update")
        gen.writeFieldName("event")
        provider.defaultSerializeValue(value.event, gen)
      }
    }
    gen.writeEndObject()
  }
}
