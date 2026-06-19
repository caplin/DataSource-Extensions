package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class VersionedMapEventSerializer(config: Config, type: Class<VersionedMapEvent<*, *>>) :
    Serializer<VersionedMapEvent<*, *>>(config, type) {

  private enum class Type {
    UPSERT,
    REMOVED,
  }

  override fun write(writeContext: WriteContext, value: VersionedMapEvent<*, *>) {
    when (value) {
      is VersionedMapEvent.Upsert -> {
        writeContext.writeByte(Type.UPSERT.ordinal.toByte())
        writeContext.writeRef(value.key)
        writeContext.writeRef(value.value)
        writeContext.writeRef(value.version)
      }
      is VersionedMapEvent.Removed -> {
        writeContext.writeByte(Type.REMOVED.ordinal.toByte())
        writeContext.writeRef(value.key)
        writeContext.writeRef(value.version)
      }
    }
  }

  override fun read(readContext: ReadContext): VersionedMapEvent<*, *> {
    return when (Type.entries[readContext.readByte().toInt()]) {
      Type.UPSERT -> {
        val key = readContext.readRef() as Any
        val value = readContext.readRef() as Any
        val version = readContext.readRef() as Long
        VersionedMapEvent.Upsert(key, value, version)
      }
      Type.REMOVED -> {
        val key = readContext.readRef() as Any
        val version = readContext.readRef() as Long
        VersionedMapEvent.Removed(key, version)
      }
    }
  }
}
