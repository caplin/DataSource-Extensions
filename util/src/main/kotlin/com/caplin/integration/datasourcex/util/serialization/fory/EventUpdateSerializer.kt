package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

/** A Fory [Serializer] for [FlowMapStreamEvent.EventUpdate]. */
internal class EventUpdateSerializer(
    fory: Fory,
    type: Class<FlowMapStreamEvent.EventUpdate<*, *>>,
) : Serializer<FlowMapStreamEvent.EventUpdate<*, *>>(fory, type) {

  override fun write(buffer: MemoryBuffer, value: FlowMapStreamEvent.EventUpdate<*, *>) {
    fory.writeRef(buffer, value.event)
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(buffer: MemoryBuffer): FlowMapStreamEvent.EventUpdate<*, *> {
    val event = fory.readRef(buffer) as MapEvent.EntryEvent<Any, Any>
    return FlowMapStreamEvent.EventUpdate(event)
  }
}
