package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

/** A Fory [Serializer] for [FlowMapStreamEvent.InitialState]. */
internal class InitialStateSerializer(
    fory: Fory,
    type: Class<FlowMapStreamEvent.InitialState<*, *>>,
) : Serializer<FlowMapStreamEvent.InitialState<*, *>>(fory, type) {

  override fun write(buffer: MemoryBuffer, value: FlowMapStreamEvent.InitialState<*, *>) {
    fory.writeRef(buffer, value.map)
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(buffer: MemoryBuffer): FlowMapStreamEvent.InitialState<*, *> {
    val map = fory.readRef(buffer) as Map<Any, Any>
    return FlowMapStreamEvent.InitialState(map)
  }
}
