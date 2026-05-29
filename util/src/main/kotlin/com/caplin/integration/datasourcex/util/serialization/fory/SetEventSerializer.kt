package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.SetEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class SetEventSerializer(config: Config, type: Class<SetEvent<*>>) :
    Serializer<SetEvent<*>>(config, type) {

  private enum class Type {
    POPULATED,
    INSERT,
    REMOVED,
  }

  override fun write(writeContext: WriteContext, value: SetEvent<*>) {
    when (value) {
      is SetEvent.Populated -> {
        writeContext.writeByte(Type.POPULATED.ordinal.toByte())
      }
      is SetEvent.EntryEvent.Insert -> {
        writeContext.writeByte(Type.INSERT.ordinal.toByte())
        writeContext.writeRef(value.value)
      }
      is SetEvent.EntryEvent.Removed -> {
        writeContext.writeByte(Type.REMOVED.ordinal.toByte())
        writeContext.writeRef(value.value)
      }
    }
  }

  override fun read(readContext: ReadContext): SetEvent<*> {
    return when (Type.entries[readContext.readByte().toInt()]) {
      Type.POPULATED -> SetEvent.Populated
      Type.INSERT -> {
        val value = readContext.readRef() as Any
        SetEvent.EntryEvent.Insert(value)
      }
      Type.REMOVED -> {
        val value = readContext.readRef() as Any
        SetEvent.EntryEvent.Removed(value)
      }
    }
  }
}
