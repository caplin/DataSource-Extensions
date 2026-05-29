package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

/** A Fory [Serializer] for [FlowMapStreamEvent.InitialState]. */
internal class InitialStateSerializer(
    config: Config,
    type: Class<FlowMapStreamEvent.InitialState<*, *>>,
) : Serializer<FlowMapStreamEvent.InitialState<*, *>>(config, type) {

  override fun write(writeContext: WriteContext, value: FlowMapStreamEvent.InitialState<*, *>) {
    writeContext.writeRef(value.map)
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(readContext: ReadContext): FlowMapStreamEvent.InitialState<*, *> {
    val map = readContext.readRef() as Map<Any, Any>
    return FlowMapStreamEvent.InitialState(map)
  }
}
