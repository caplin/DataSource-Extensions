package com.caplin.integration.datasourcex.util.flow

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import kotlinx.collections.immutable.toPersistentMap

fun ObjectMapper.registerDataSourceModule(): ObjectMapper = registerModule(DataSourceJacksonModule)

/**
 * A Jackson [Module] that provides support for serializing and deserializing [FlowMapStreamEvent]
 * and [MapEvent] without requiring annotations on the classes themselves.
 */
object DataSourceJacksonModule : SimpleModule() {
  private fun readResolve(): Any = DataSourceJacksonModule

  init {
    addSerializer(FlowMapStreamEvent::class.java, FlowMapStreamEventSerializer())
    addDeserializer(FlowMapStreamEvent::class.java, FlowMapStreamEventDeserializer())

    addSerializer(MapEvent::class.java, MapEventSerializer())
    addDeserializer(MapEvent::class.java, MapEventDeserializer())
  }
}

private class FlowMapStreamEventSerializer :
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

private class FlowMapStreamEventDeserializer :
    StdDeserializer<FlowMapStreamEvent<*, *>>(FlowMapStreamEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FlowMapStreamEvent<*, *> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for FlowMapStreamEvent")
    return when (type) {
      "initial" -> {
        val mapNode =
            node.get("map") ?: throw JsonMappingException.from(p, "Missing map field for initial")
        val map = p.codec.treeToValue(mapNode, Map::class.java) as Map<Any, Any>
        FlowMapStreamEvent.InitialState(map.toPersistentMap())
      }
      "update" -> {
        val eventNode =
            node.get("event")
                ?: throw JsonMappingException.from(p, "Missing event field for update")
        val event = p.codec.treeToValue(eventNode, MapEvent::class.java) as MapEvent<Any, Any>
        FlowMapStreamEvent.EventUpdate(event)
      }
      else -> throw JsonMappingException.from(p, "Unknown type: $type")
    }
  }
}

private class MapEventSerializer : StdSerializer<MapEvent<*, *>>(MapEvent::class.java) {
  override fun serialize(value: MapEvent<*, *>, gen: JsonGenerator, provider: SerializerProvider) {
    gen.writeStartObject()
    when (value) {
      is MapEvent.Populated -> {
        gen.writeStringField("type", "populated")
      }
      is MapEvent.EntryEvent.Upsert -> {
        gen.writeStringField("type", "upsert")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeFieldName("oldValue")
        provider.defaultSerializeValue(value.oldValue, gen)
        gen.writeFieldName("newValue")
        provider.defaultSerializeValue(value.newValue, gen)
      }
      is MapEvent.EntryEvent.Removed -> {
        gen.writeStringField("type", "removed")
        gen.writeFieldName("key")
        provider.defaultSerializeValue(value.key, gen)
        gen.writeFieldName("oldValue")
        provider.defaultSerializeValue(value.oldValue, gen)
      }
    }
    gen.writeEndObject()
  }
}

private class MapEventDeserializer : StdDeserializer<MapEvent<*, *>>(MapEvent::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MapEvent<*, *> {
    val node = p.codec.readTree<ObjectNode>(p)
    val type =
        node.get("type")?.asText()
            ?: throw JsonMappingException.from(p, "Missing type field for MapEvent")
    return when (type) {
      "populated" -> MapEvent.Populated
      "upsert" -> {
        val key =
            node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing key field for upsert")
        val oldValueNode = node.get("oldValue")
        val oldValue =
            if (oldValueNode == null || oldValueNode.isNull) null
            else p.codec.treeToValue(oldValueNode, Any::class.java)
        val newValue =
            node.get("newValue")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing newValue field for upsert")
        MapEvent.EntryEvent.Upsert(key, oldValue, newValue)
      }
      "removed" -> {
        val key =
            node.get("key")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing key field for removed")
        val oldValue =
            node.get("oldValue")?.let { p.codec.treeToValue(it, Any::class.java) }
                ?: throw JsonMappingException.from(p, "Missing oldValue field for removed")
        MapEvent.EntryEvent.Removed(key, oldValue)
      }
      else -> throw JsonMappingException.from(p, "Unknown MapEvent type: $type")
    }
  }
}
