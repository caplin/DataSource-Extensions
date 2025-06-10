package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import java.io.Serializable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

/**
 * Represents a materialized [Flow] event.
 *
 * Be careful about using this within [SharedFlow] or [StateFlow] as these values will be included
 * in the replay cache.
 *
 * @see materialize
 * @see dematerialize
 * @see materializeUnboxed
 * @see dematerializeUnboxed
 */
sealed interface ValueOrCompletion<out T : Any?> : Serializable {

  @Suppress("UNCHECKED_CAST")
  suspend fun <R : Any?> map(block: suspend (T) -> R): ValueOrCompletion<R> =
      this as ValueOrCompletion<R>

  class Value<out T : Any?>(val value: T) : ValueOrCompletion<T> {
    operator fun component1(): T = value

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R : Any?> map(block: suspend (T) -> R): ValueOrCompletion<R> {
      val result = block(value)
      return when {
        result === value -> this as ValueOrCompletion<R>
        else -> Value(result)
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Value<*>

      return value == other.value
    }

    override fun hashCode(): Int {
      return value?.hashCode() ?: 0
    }

    override fun toString(): String = "Value($value)"
  }

  class Completion(val throwable: Throwable? = null) : ValueOrCompletion<Nothing> {
    operator fun component1(): Throwable? = throwable

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Completion

      return throwable == other.throwable
    }

    override fun hashCode(): Int {
      return throwable?.hashCode() ?: 0
    }

    override fun toString(): String = "Completion($throwable)"
  }
}

/**
 * Materialize [Flow] events into [ValueOrCompletion]s that can be sent over the wire, for example.
 */
@Suppress("TooGenericExceptionCaught")
fun <T> Flow<T>.materialize(): Flow<ValueOrCompletion<T>> = flow {
  var cause: Exception? = null
  try {
    collect { emit(Value(it)) }
  } catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    cause = e
  }
  emit(Completion(cause))
}

/** Dematerializes [ValueOrCompletion]s back into their counterpart [Flow] events. */
fun <T> Flow<ValueOrCompletion<T>>.dematerialize(): Flow<T> = transformWhile { valueOrCompletion ->
  when (valueOrCompletion) {
    is Value -> {
      emit(valueOrCompletion.value)
      true
    }

    is Completion -> {
      valueOrCompletion.throwable?.let { throw it }
      false
    }
  }
}

/**
 * Specialised version of [materialize] which does not box updates. Loses type information so
 * corresponding [dematerializeUnboxed] must know what type to cast updates to.
 */
@DelicateCoroutinesApi
@Suppress("TooGenericExceptionCaught")
fun Flow<*>.materializeUnboxed(): Flow<Any?> = flow {
  var cause: Exception? = null
  try {
    collect { emit(it) }
  } catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    cause = e
  }
  emit(Completion(cause))
}

/**
 * Specialised version of [dematerialize] which expects unboxed updates. Type information is lost
 * with [materializeUnboxed], so must know what type to cast updates to.
 */
@DelicateCoroutinesApi
fun <T> Flow<Any?>.dematerializeUnboxed(): Flow<T> =
    transformWhile { valueOrCompletion ->
          when (valueOrCompletion) {
            is Completion -> {
              valueOrCompletion.throwable?.let { throw it }
              false
            }

            else -> {
              emit(valueOrCompletion)
              true
            }
          }
        }
        .cast()
