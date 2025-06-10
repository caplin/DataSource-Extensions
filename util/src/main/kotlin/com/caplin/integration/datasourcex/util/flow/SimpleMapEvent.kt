package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.EntryEvent
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.SimpleMapEvent.Populated
import com.caplin.integration.datasourcex.util.serializable
import java.io.Serializable
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Events representing a mutation to a [Map].
 *
 * Unlike [MapEvent.EntryEvent] - [EntryEvent] does not carry the old value, and as such Flows
 * cannot be conflated via conflateKeys.
 *
 * The primary usecase of this is for creating a [Flow] of [Map]s from a series of events via
 * [runningFoldToMap].
 *
 * This does not support maps with `null` values or keys, consider using [java.util.Optional] if
 * this is required.
 */
sealed interface SimpleMapEvent<out K : Any, out V : Any> : Serializable {

  /**
   * Indicates that a consistent view of the map has been emitted and only updates will be seen from
   * now on.
   *
   * Note that if [conflateKeys] is used, there may be [Removed] events seen before the [Populated]
   * event.
   */
  object Populated : SimpleMapEvent<Nothing, Nothing> {
    private fun readResolve(): Any = Populated

    override fun toString(): String {
      return "Populated()"
    }
  }

  /** Mutation event for a specific entry. */
  sealed interface EntryEvent<out K : Any, out V : Any> : SimpleMapEvent<K, V> {
    val key: K
    val newValue: V?

    /** Entry with [key] has been updated to [newValue]. */
    class Upsert<out K : Any, out V : Any>(override val key: K, override val newValue: V) :
        EntryEvent<K, V> {

      operator fun component1(): K = key

      operator fun component2(): V = newValue

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Upsert<*, *>

        if (key != other.key) return false
        if (newValue != other.newValue) return false

        return true
      }

      override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + newValue.hashCode()
        return result
      }

      override fun toString(): String {
        return "Upsert(key=$key, newValue=$newValue)"
      }
    }

    /** Entry with [key] has been removed */
    class Removed<out K : Any, out V : Any>(override val key: K) : EntryEvent<K, V> {
      override val newValue: V? = null

      operator fun component1(): K = key

      operator fun component2(): V? = newValue

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Removed<*, *>

        return key == other.key
      }

      override fun hashCode(): Int {
        return key.hashCode()
      }

      override fun toString(): String {
        return "Removed(key=$key)"
      }
    }
  }
}

/**
 * Folds a flow of [MapEvent]s into a flow of [Map].
 *
 * When [emitPartials] is `false`, it will not emit the first [Map] until a [Populated] event is
 * received.
 */
@JvmName("runningFoldToMapSimpleMapEvent")
fun <K : Any, V : Any> Flow<SimpleMapEvent<K, V>>.runningFoldToMap(
    emitPartials: Boolean = false
): Flow<Map<K, V>> = flow {
  var emitted = false
  var populated = false
  var map = persistentMapOf<K, V>()

  collect { mapEvent ->
    var emit = false
    when (mapEvent) {
      is Removed -> {
        map =
            map.remove(mapEvent.key).also { newMap ->
              check(newMap !== map) { "Attempted to remove non existent key ${mapEvent.key}" }
            }
        if (populated || emitPartials) emit = true
      }

      is Upsert -> {
        map = map.put(mapEvent.key, mapEvent.newValue)
        if (populated || emitPartials) emit = true
      }

      is Populated -> {
        populated = true
        if (!emitted || !emitPartials) emit = true
      }
    }
    if (emit) {
      emitted = true
      emit(map.serializable())
    }
  }
}

/** Folds a flow of [EntryEvent]s into a flow of [Map]. */
@JvmName("runningFoldToMapSimpleEntryEvent")
fun <K : Any, V : Any> Flow<EntryEvent<K, V>>.runningFoldToMap(): Flow<Map<K, V>> = flow {
  var map = persistentMapOf<K, V>()

  collect { mapEvent ->
    when (mapEvent) {
      is Removed -> {
        map =
            map.remove(mapEvent.key).also { newMap ->
              check(newMap !== map) { "Attempted to remove non existent key ${mapEvent.key}" }
            }
        emit(map.serializable())
      }

      is Upsert -> {
        map = map.put(mapEvent.key, mapEvent.newValue)
        emit(map.serializable())
      }
    }
  }
}
