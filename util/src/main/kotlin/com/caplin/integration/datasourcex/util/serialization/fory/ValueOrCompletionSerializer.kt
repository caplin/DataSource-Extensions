package com.caplin.integration.datasourcex.util.serialization.fory

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.Serializer

internal class ValueOrCompletionSerializer(
    config: Config,
    type: Class<ValueOrCompletion<*>>,
    private val preserveExceptionTypes: Boolean = true,
) : Serializer<ValueOrCompletion<*>>(config, type) {

  private enum class Type {
    VALUE,
    COMPLETION,
  }

  override fun write(writeContext: WriteContext, value: ValueOrCompletion<*>) {
    when (value) {
      is ValueOrCompletion.Value -> {
        writeContext.writeByte(Type.VALUE.ordinal.toByte())
        writeContext.writeRef(value.value)
      }
      is ValueOrCompletion.Completion -> {
        writeContext.writeByte(Type.COMPLETION.ordinal.toByte())
        if (preserveExceptionTypes) {
          writeContext.writeRef(value.throwable)
        } else {
          val message = value.throwable?.message ?: value.throwable?.toString()
          writeContext.writeRef(message)
        }
      }
    }
  }

  override fun read(readContext: ReadContext): ValueOrCompletion<*> {
    return when (Type.entries[readContext.readByte().toInt()]) {
      Type.VALUE -> {
        val value = readContext.readRef()
        ValueOrCompletion.Value(value)
      }
      Type.COMPLETION -> {
        if (preserveExceptionTypes) {
          val throwable = readContext.readRef() as Throwable?
          ValueOrCompletion.Completion(throwable)
        } else {
          val message = readContext.readRef() as String?
          ValueOrCompletion.Completion(message?.let { RuntimeException(it) })
        }
      }
    }
  }
}
