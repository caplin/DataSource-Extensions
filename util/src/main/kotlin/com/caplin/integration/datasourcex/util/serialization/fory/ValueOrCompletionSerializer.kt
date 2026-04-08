package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import org.apache.fory.Fory
import org.apache.fory.memory.MemoryBuffer
import org.apache.fory.serializer.Serializer

internal class ValueOrCompletionSerializer(
    fory: Fory,
    type: Class<ValueOrCompletion<*>>,
    private val preserveExceptionTypes: Boolean = true,
) : Serializer<ValueOrCompletion<*>>(fory, type) {

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
        if (preserveExceptionTypes) {
          fory.writeRef(buffer, value.throwable)
        } else {
          val message = value.throwable?.message ?: value.throwable?.toString()
          fory.writeRef(buffer, message)
        }
      }
    }
  }

  override fun read(buffer: MemoryBuffer): ValueOrCompletion<*> {
    return when (Type.entries[buffer.readByte().toInt()]) {
      Type.VALUE -> {
        val value = fory.readRef(buffer) as Any
        ValueOrCompletion.Value(value)
      }
      Type.COMPLETION -> {
        if (preserveExceptionTypes) {
          val throwable = fory.readRef(buffer) as Throwable?
          ValueOrCompletion.Completion(throwable)
        } else {
          val message = fory.readRef(buffer) as String?
          ValueOrCompletion.Completion(message?.let { RuntimeException(it) })
        }
      }
    }
  }
}
