package com.caplin.integration.datasourcex.util.serialization.fory

import org.apache.fory.resolver.TypeResolver
import org.apache.fory.serializer.ObjectSerializer
import org.apache.fory.serializer.Serializer
import org.apache.fory.serializer.SerializerFactory

/**
 * Serializes [Throwable] types with a null-rejecting `(String)` constructor via [ObjectSerializer],
 * which allocates without calling the constructor.
 *
 * Fory's default exception serializer instantiates such types by calling that constructor with
 * `null`, which fails for a non-nullable Kotlin `String` parameter. All other throwables fall
 * through to Fory's default.
 */
internal object ThrowableSerializerFactory : SerializerFactory {

  override fun createSerializer(typeResolver: TypeResolver, cls: Class<*>): Serializer<*>? {
    if (!Throwable::class.java.isAssignableFrom(cls)) return null
    if (!rejectsNullMessage(cls)) return null
    return ObjectSerializer(typeResolver, cls)
  }

  private fun rejectsNullMessage(cls: Class<*>): Boolean {
    // Only Kotlin classes emit non-null parameter checks; Java constructors accept a null message.
    if (!cls.isAnnotationPresent(Metadata::class.java)) return false
    return runCatching {
          cls.kotlin.constructors.any { constructor ->
            val parameter = constructor.parameters.singleOrNull() ?: return@any false
            parameter.type.classifier == String::class && !parameter.type.isMarkedNullable
          }
        }
        .getOrDefault(false)
  }
}
