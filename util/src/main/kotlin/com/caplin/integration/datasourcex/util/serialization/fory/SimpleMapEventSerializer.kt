package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class SimpleMapEventSerializer(config: Config, type: Class<SimpleMapEvent<*, *>>) :
    Serializer<SimpleMapEvent<*, *>>(config, type) {

  private enum class Type {
    POPULATED,
    UPSERT,
    REMOVED,
  }

  override fun write(writeContext: WriteContext, value: SimpleMapEvent<*, *>) {
    when (value) {
      is SimpleMapEvent.Populated -> {
        writeContext.writeByte(Type.POPULATED.ordinal.toByte())
      }
      is SimpleMapEvent.EntryEvent.Upsert -> {
        writeContext.writeByte(Type.UPSERT.ordinal.toByte())
        writeContext.writeRef(value.key)
        writeContext.writeRef(value.newValue)
      }
      is SimpleMapEvent.EntryEvent.Removed -> {
        writeContext.writeByte(Type.REMOVED.ordinal.toByte())
        writeContext.writeRef(value.key)
      }
    }
  }

  override fun read(readContext: ReadContext): SimpleMapEvent<*, *> {
    return when (Type.entries[readContext.readByte().toInt()]) {
      Type.POPULATED -> SimpleMapEvent.Populated
      Type.UPSERT -> {
        val key = readContext.readRef() as Any
        val newValue = readContext.readRef() as Any
        SimpleMapEvent.EntryEvent.Upsert(key, newValue)
      }
      Type.REMOVED -> {
        val key = readContext.readRef() as Any
        SimpleMapEvent.EntryEvent.Removed<Any, Any>(key)
      }
    }
  }
}
