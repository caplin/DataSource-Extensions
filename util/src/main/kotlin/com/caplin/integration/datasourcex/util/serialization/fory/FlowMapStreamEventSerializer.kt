package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

internal class FlowMapStreamEventSerializer(fory: Fory, type: Class<FlowMapStreamEvent<*, *>>) :
    Serializer<FlowMapStreamEvent<*, *>>(fory, type) {

  private enum class Type {
    INITIAL_STATE,
    EVENT_UPDATE,
  }

  override fun write(buffer: MemoryBuffer, value: FlowMapStreamEvent<*, *>) {
    when (value) {
      is FlowMapStreamEvent.InitialState -> {
        buffer.writeByte(Type.INITIAL_STATE.ordinal.toByte())
        fory.writeRef(buffer, value.map)
      }
      is FlowMapStreamEvent.EventUpdate -> {
        buffer.writeByte(Type.EVENT_UPDATE.ordinal.toByte())
        fory.writeRef(buffer, value.event)
      }
    }
  }

  override fun read(buffer: MemoryBuffer): FlowMapStreamEvent<*, *> {
    return when (Type.entries[buffer.readByte().toInt()]) {
      Type.INITIAL_STATE -> {
        val map = fory.readRef(buffer) as Map<Any, Any>
        FlowMapStreamEvent.InitialState(map)
      }
      Type.EVENT_UPDATE -> {
        val event = fory.readRef(buffer) as MapEvent.EntryEvent<Any, Any>
        FlowMapStreamEvent.EventUpdate(event)
      }
    }
  }
}
