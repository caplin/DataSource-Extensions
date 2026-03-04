package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.MapEvent
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

internal class MapEventSerializer(fory: Fory, type: Class<MapEvent<*, *>>) :
    Serializer<MapEvent<*, *>>(fory, type) {

  private enum class Type {
    POPULATED,
    UPSERT,
    REMOVED,
  }

  override fun write(buffer: MemoryBuffer, value: MapEvent<*, *>) {
    when (value) {
      is MapEvent.Populated -> {
        buffer.writeByte(Type.POPULATED.ordinal.toByte())
      }
      is MapEvent.EntryEvent.Upsert -> {
        buffer.writeByte(Type.UPSERT.ordinal.toByte())
        fory.writeRef(buffer, value.key)
        fory.writeRef(buffer, value.oldValue)
        fory.writeRef(buffer, value.newValue)
      }
      is MapEvent.EntryEvent.Removed -> {
        buffer.writeByte(Type.REMOVED.ordinal.toByte())
        fory.writeRef(buffer, value.key)
        fory.writeRef(buffer, value.oldValue)
      }
    }
  }

  override fun read(buffer: MemoryBuffer): MapEvent<*, *> {
    return when (Type.values()[buffer.readByte().toInt()]) {
      Type.POPULATED -> MapEvent.Populated
      Type.UPSERT -> {
        val key = fory.readRef(buffer) as Any
        val oldValue = fory.readRef(buffer)
        val newValue = fory.readRef(buffer) as Any
        MapEvent.EntryEvent.Upsert(key, oldValue, newValue)
      }
      Type.REMOVED -> {
        val key = fory.readRef(buffer) as Any
        val oldValue = fory.readRef(buffer) as Any
        MapEvent.EntryEvent.Removed(key, oldValue)
      }
    }
  }
}
