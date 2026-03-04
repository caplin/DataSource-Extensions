package com.caplin.integration.datasourcex.util.serialization.jackson

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.caplin.integration.datasourcex.util.flow.SetEvent
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule

fun ObjectMapper.registerDataSourceModule(): ObjectMapper = registerModule(DataSourceModule)

/**
 * A Jackson [Module] that provides support for serializing and deserializing DataSource types
 * without requiring annotations on the classes themselves.
 */
object DataSourceModule : SimpleModule() {
  private fun readResolve(): Any = DataSourceModule

  init {
    addSerializer(FlowMapStreamEvent::class.java, FlowMapStreamEventSerializer())
    addDeserializer(FlowMapStreamEvent::class.java, FlowMapStreamEventDeserializer())

    addSerializer(MapEvent::class.java, MapEventSerializer())
    addDeserializer(MapEvent::class.java, MapEventDeserializer())

    addSerializer(SimpleMapEvent::class.java, SimpleMapEventSerializer())
    addDeserializer(SimpleMapEvent::class.java, SimpleMapEventDeserializer())

    addSerializer(SetEvent::class.java, SetEventSerializer())
    addDeserializer(SetEvent::class.java, SetEventDeserializer())

    addSerializer(ValueOrCompletion::class.java, ValueOrCompletionSerializer())
    addDeserializer(ValueOrCompletion::class.java, ValueOrCompletionDeserializer())
  }
}
