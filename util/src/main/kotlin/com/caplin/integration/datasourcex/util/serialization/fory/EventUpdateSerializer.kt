package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.FlowMapStreamEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

/** A Fory [Serializer] for [FlowMapStreamEvent.EventUpdate]. */
internal class EventUpdateSerializer(
    config: Config,
    type: Class<FlowMapStreamEvent.EventUpdate<*, *>>,
) : Serializer<FlowMapStreamEvent.EventUpdate<*, *>>(config, type) {

  override fun write(writeContext: WriteContext, value: FlowMapStreamEvent.EventUpdate<*, *>) {
    writeContext.writeRef(value.event)
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(readContext: ReadContext): FlowMapStreamEvent.EventUpdate<*, *> {
    val event = readContext.readRef() as MapEvent.EntryEvent<Any, Any>
    return FlowMapStreamEvent.EventUpdate(event)
  }
}
