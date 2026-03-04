package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

internal class ValueOrCompletionSerializer(fory: Fory, type: Class<ValueOrCompletion<*>>) :
    Serializer<ValueOrCompletion<*>>(fory, type) {

  private enum class Type {
    VALUE,
    COMPLETION,
  }

  override fun write(buffer: MemoryBuffer, value: ValueOrCompletion<*>) {
    when (value) {
      is ValueOrCompletion.Value -> {
        buffer.writeByte(Type.VALUE.ordinal.toByte())
        fory.writeRef(buffer, value.value)
      }
      is ValueOrCompletion.Completion -> {
        buffer.writeByte(Type.COMPLETION.ordinal.toByte())
        val message = value.throwable?.message ?: value.throwable?.toString()
        fory.writeRef(buffer, message)
      }
    }
  }

  override fun read(buffer: MemoryBuffer): ValueOrCompletion<*> {
    return when (Type.values()[buffer.readByte().toInt()]) {
      Type.VALUE -> {
        val value = fory.readRef(buffer) as Any
        ValueOrCompletion.Value(value)
      }
      Type.COMPLETION -> {
        val message = fory.readRef(buffer) as String?
        ValueOrCompletion.Completion(message?.let { RuntimeException(it) })
      }
    }
  }
}
