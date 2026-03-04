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
  @Suppress("unused") private fun readResolve(): Any = DataSourceModule

  init {
    addSerializer(FlowMapStreamEvent::class.java, FlowMapStreamEventSerializer())
    addDeserializer(FlowMapStreamEvent::class.java, FlowMapStreamEventDeserializer())

    val mapEventSerializer = MapEventSerializer()
    val mapEventDeserializer = MapEventDeserializer()

    addSerializer(MapEvent::class.java, mapEventSerializer)
    addDeserializer(MapEvent::class.java, mapEventDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        MapEvent.EntryEvent::class.java,
        mapEventSerializer
            as com.fasterxml.jackson.databind.JsonSerializer<MapEvent.EntryEvent<*, *>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        MapEvent.EntryEvent::class.java,
        mapEventDeserializer
            as com.fasterxml.jackson.databind.JsonDeserializer<MapEvent.EntryEvent<*, *>>,
    )

    val simpleMapEventSerializer = SimpleMapEventSerializer()
    val simpleMapEventDeserializer = SimpleMapEventDeserializer()

    addSerializer(SimpleMapEvent::class.java, simpleMapEventSerializer)
    addDeserializer(SimpleMapEvent::class.java, simpleMapEventDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        SimpleMapEvent.EntryEvent::class.java,
        simpleMapEventSerializer
            as com.fasterxml.jackson.databind.JsonSerializer<SimpleMapEvent.EntryEvent<*, *>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        SimpleMapEvent.EntryEvent::class.java,
        simpleMapEventDeserializer
            as com.fasterxml.jackson.databind.JsonDeserializer<SimpleMapEvent.EntryEvent<*, *>>,
    )

    val setEventSerializer = SetEventSerializer()
    val setEventDeserializer = SetEventDeserializer()

    addSerializer(SetEvent::class.java, setEventSerializer)
    addDeserializer(SetEvent::class.java, setEventDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        SetEvent.EntryEvent::class.java,
        setEventSerializer as com.fasterxml.jackson.databind.JsonSerializer<SetEvent.EntryEvent<*>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        SetEvent.EntryEvent::class.java,
        setEventDeserializer
            as com.fasterxml.jackson.databind.JsonDeserializer<SetEvent.EntryEvent<*>>,
    )

    val valueOrCompletionSerializer = ValueOrCompletionSerializer()
    val valueOrCompletionDeserializer = ValueOrCompletionDeserializer()

    addSerializer(ValueOrCompletion::class.java, valueOrCompletionSerializer)
    addDeserializer(ValueOrCompletion::class.java, valueOrCompletionDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        ValueOrCompletion.Value::class.java,
        valueOrCompletionSerializer
            as com.fasterxml.jackson.databind.JsonSerializer<ValueOrCompletion.Value<*>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        ValueOrCompletion.Value::class.java,
        valueOrCompletionDeserializer
            as com.fasterxml.jackson.databind.JsonDeserializer<ValueOrCompletion.Value<*>>,
    )

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        ValueOrCompletion.Completion::class.java,
        valueOrCompletionSerializer
            as com.fasterxml.jackson.databind.JsonSerializer<ValueOrCompletion.Completion>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        ValueOrCompletion.Completion::class.java,
        valueOrCompletionDeserializer
            as com.fasterxml.jackson.databind.JsonDeserializer<ValueOrCompletion.Completion>,
    )
  }
}
