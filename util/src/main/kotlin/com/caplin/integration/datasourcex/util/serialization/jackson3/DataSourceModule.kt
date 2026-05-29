package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import com.caplin.integration.datasourcex.util.flow.SetEvent
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

/**
 * Adds the DataSource [DataSourceModule] to this Jackson 3 [JsonMapper.Builder].
 *
 * Jackson 3 mappers are immutable, so the module must be registered while building the mapper
 * rather than on an existing one (the Jackson 2 equivalent is
 * `ObjectMapper.registerDataSourceModule()`).
 */
fun JsonMapper.Builder.addDataSourceModule(): JsonMapper.Builder = addModule(DataSourceModule)

/**
 * A Jackson 3 module that provides support for serializing and deserializing DataSource types
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
        mapEventSerializer as ValueSerializer<MapEvent.EntryEvent<*, *>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        MapEvent.EntryEvent::class.java,
        mapEventDeserializer as ValueDeserializer<MapEvent.EntryEvent<*, *>>,
    )

    val simpleMapEventSerializer = SimpleMapEventSerializer()
    val simpleMapEventDeserializer = SimpleMapEventDeserializer()

    addSerializer(SimpleMapEvent::class.java, simpleMapEventSerializer)
    addDeserializer(SimpleMapEvent::class.java, simpleMapEventDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        SimpleMapEvent.EntryEvent::class.java,
        simpleMapEventSerializer as ValueSerializer<SimpleMapEvent.EntryEvent<*, *>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        SimpleMapEvent.EntryEvent::class.java,
        simpleMapEventDeserializer as ValueDeserializer<SimpleMapEvent.EntryEvent<*, *>>,
    )

    val setEventSerializer = SetEventSerializer()
    val setEventDeserializer = SetEventDeserializer()

    addSerializer(SetEvent::class.java, setEventSerializer)
    addDeserializer(SetEvent::class.java, setEventDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        SetEvent.EntryEvent::class.java,
        setEventSerializer as ValueSerializer<SetEvent.EntryEvent<*>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        SetEvent.EntryEvent::class.java,
        setEventDeserializer as ValueDeserializer<SetEvent.EntryEvent<*>>,
    )

    val valueOrCompletionSerializer = ValueOrCompletionSerializer()
    val valueOrCompletionDeserializer = ValueOrCompletionDeserializer()

    addSerializer(ValueOrCompletion::class.java, valueOrCompletionSerializer)
    addDeserializer(ValueOrCompletion::class.java, valueOrCompletionDeserializer)

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        ValueOrCompletion.Value::class.java,
        valueOrCompletionSerializer as ValueSerializer<ValueOrCompletion.Value<*>>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        ValueOrCompletion.Value::class.java,
        valueOrCompletionDeserializer as ValueDeserializer<ValueOrCompletion.Value<*>>,
    )

    @Suppress("UNCHECKED_CAST")
    addSerializer(
        ValueOrCompletion.Completion::class.java,
        valueOrCompletionSerializer as ValueSerializer<ValueOrCompletion.Completion>,
    )
    @Suppress("UNCHECKED_CAST")
    addDeserializer(
        ValueOrCompletion.Completion::class.java,
        valueOrCompletionDeserializer as ValueDeserializer<ValueOrCompletion.Completion>,
    )
  }
}
