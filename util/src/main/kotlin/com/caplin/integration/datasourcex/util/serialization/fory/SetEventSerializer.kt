package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.SetEvent
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

internal class SetEventSerializer(fory: Fory, type: Class<SetEvent<*>>) :
    Serializer<SetEvent<*>>(fory, type) {

  private enum class Type {
    POPULATED,
    INSERT,
    REMOVED,
  }

  override fun write(buffer: MemoryBuffer, value: SetEvent<*>) {
    when (value) {
      is SetEvent.Populated -> {
        buffer.writeByte(Type.POPULATED.ordinal.toByte())
      }
      is SetEvent.EntryEvent.Insert -> {
        buffer.writeByte(Type.INSERT.ordinal.toByte())
        fory.writeRef(buffer, value.value)
      }
      is SetEvent.EntryEvent.Removed -> {
        buffer.writeByte(Type.REMOVED.ordinal.toByte())
        fory.writeRef(buffer, value.value)
      }
    }
  }

  override fun read(buffer: MemoryBuffer): SetEvent<*> {
    return when (Type.values()[buffer.readByte().toInt()]) {
      Type.POPULATED -> SetEvent.Populated
      Type.INSERT -> {
        val value = fory.readRef(buffer) as Any
        SetEvent.EntryEvent.Insert(value)
      }
      Type.REMOVED -> {
        val value = fory.readRef(buffer) as Any
        SetEvent.EntryEvent.Removed(value)
      }
    }
  }
}
