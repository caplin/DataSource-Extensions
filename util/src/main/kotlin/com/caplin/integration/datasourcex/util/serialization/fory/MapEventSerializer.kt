package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.MapEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class MapEventSerializer(config: Config, type: Class<MapEvent<*, *>>) :
    Serializer<MapEvent<*, *>>(config, type) {

  private enum class Type {
    POPULATED,
    UPSERT,
    REMOVED,
  }

  override fun write(writeContext: WriteContext, value: MapEvent<*, *>) {
    when (value) {
      is MapEvent.Populated -> {
        writeContext.writeByte(Type.POPULATED.ordinal.toByte())
      }
      is MapEvent.EntryEvent.Upsert -> {
        writeContext.writeByte(Type.UPSERT.ordinal.toByte())
        writeContext.writeRef(value.key)
        writeContext.writeRef(value.oldValue)
        writeContext.writeRef(value.newValue)
      }
      is MapEvent.EntryEvent.Removed -> {
        writeContext.writeByte(Type.REMOVED.ordinal.toByte())
        writeContext.writeRef(value.key)
        writeContext.writeRef(value.oldValue)
      }
    }
  }

  override fun read(readContext: ReadContext): MapEvent<*, *> {
    return when (Type.entries[readContext.readByte().toInt()]) {
      Type.POPULATED -> MapEvent.Populated
      Type.UPSERT -> {
        val key = readContext.readRef() as Any
        val oldValue = readContext.readRef()
        val newValue = readContext.readRef() as Any
        MapEvent.EntryEvent.Upsert(key, oldValue, newValue)
      }
      Type.REMOVED -> {
        val key = readContext.readRef() as Any
        val oldValue = readContext.readRef() as Any
        MapEvent.EntryEvent.Removed(key, oldValue)
      }
    }
  }
}
