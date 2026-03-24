package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

internal class SimpleMapEventSerializer(fory: Fory, type: Class<SimpleMapEvent<*, *>>) :
    Serializer<SimpleMapEvent<*, *>>(fory, type) {

  private enum class Type {
    POPULATED,
    UPSERT,
    REMOVED,
  }

  override fun write(buffer: MemoryBuffer, value: SimpleMapEvent<*, *>) {
    when (value) {
      is SimpleMapEvent.Populated -> {
        buffer.writeByte(Type.POPULATED.ordinal.toByte())
      }
      is SimpleMapEvent.EntryEvent.Upsert -> {
        buffer.writeByte(Type.UPSERT.ordinal.toByte())
        fory.writeRef(buffer, value.key)
        fory.writeRef(buffer, value.newValue)
      }
      is SimpleMapEvent.EntryEvent.Removed -> {
        buffer.writeByte(Type.REMOVED.ordinal.toByte())
        fory.writeRef(buffer, value.key)
      }
    }
  }

  override fun read(buffer: MemoryBuffer): SimpleMapEvent<*, *> {
    return when (Type.entries[buffer.readByte().toInt()]) {
      Type.POPULATED -> SimpleMapEvent.Populated
      Type.UPSERT -> {
        val key = fory.readRef(buffer) as Any
        val newValue = fory.readRef(buffer) as Any
        SimpleMapEvent.EntryEvent.Upsert(key, newValue)
      }
      Type.REMOVED -> {
        val key = fory.readRef(buffer) as Any
        SimpleMapEvent.EntryEvent.Removed<Any, Any>(key)
      }
    }
  }
}
