package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class FlowMapStreamEventSerializer(config: Config, type: Class<FlowMapStreamEvent<*, *>>) :
    Serializer<FlowMapStreamEvent<*, *>>(config, type) {

  private enum class Type {
    INITIAL_STATE,
    EVENT_UPDATE,
    CLEARED,
  }

  override fun write(writeContext: WriteContext, value: FlowMapStreamEvent<*, *>) {
    when (value) {
      is FlowMapStreamEvent.InitialState -> {
        writeContext.writeByte(Type.INITIAL_STATE.ordinal.toByte())
        writeContext.writeRef(value.map)
      }
      is FlowMapStreamEvent.EventUpdate -> {
        writeContext.writeByte(Type.EVENT_UPDATE.ordinal.toByte())
        writeContext.writeRef(value.event)
      }
      is FlowMapStreamEvent.Cleared -> {
        writeContext.writeByte(Type.CLEARED.ordinal.toByte())
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(readContext: ReadContext): FlowMapStreamEvent<*, *> {
    return when (Type.entries[readContext.readByte().toInt()]) {
      Type.INITIAL_STATE -> {
        val map = readContext.readRef() as Map<Any, Any>
        FlowMapStreamEvent.InitialState(map)
      }
      Type.EVENT_UPDATE -> {
        val event = readContext.readRef() as MapEvent.EntryEvent<Any, Any>
        FlowMapStreamEvent.EventUpdate(event)
      }
      Type.CLEARED -> FlowMapStreamEvent.Cleared
    }
  }
}
