@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import com.caplin.integration.datasourcex.util.serializable
import java.io.Serializable
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.whileSelect

/**
 * Events representing a mutation to a [Map].
 *
 * Unlike [SimpleMapEvent.EntryEvent] - [EntryEvent] carries the old value.
 *
 * This does not support maps with `null` values or keys, consider using [java.util.Optional] if
 * this is required.
 */
sealed interface MapEvent<out K : Any, out V : Any> : Serializable {

  /**
   * Indicates that a consistent view of the map has been emitted and only updates will be seen from
   * now on.
   *
   * Note that if [conflateKeys] is used, there may be [Removed] events seen before the [Populated]
   * event.
   */
  object Populated : MapEvent<Nothing, Nothing> {
    private fun readResolve(): Any = Populated

    override fun toString(): String {
      return "Populated()"
    }
  }

  /** Mutation event for a specific entry. */
  sealed interface EntryEvent<out K : Any, out V : Any> : MapEvent<K, V> {
    val key: K
    val oldValue: V?
    val newValue: V?

    operator fun component1(): K = key

    operator fun component2(): V? = oldValue

    operator fun component3(): V? = newValue

    /**
     * Entry with [key] has been updated from [oldValue] to [newValue]. An insert will have a `null`
     * [oldValue].
     */
    class Upsert<out K : Any, out V : Any>(
        override val key: K,
        override val oldValue: V?,
        override val newValue: V,
    ) : EntryEvent<K, V> {

      override operator fun component3(): V = newValue

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Upsert<*, *>

        if (key != other.key) return false
        if (oldValue != other.oldValue) return false
        if (newValue != other.newValue) return false

        return true
      }

      override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        result = 31 * result + newValue.hashCode()
        return result
      }

      override fun toString(): String {
        return "Upsert(key=$key, oldValue=$oldValue, newValue=$newValue)"
      }
    }

    /** Entry with [key] and value [oldValue] has been removed */
    class Removed<out K : Any, out V : Any>(override val key: K, override val oldValue: V) :
        EntryEvent<K, V> {
      override val newValue: V? = null

      override operator fun component2(): V = oldValue

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Removed<*, *>

        if (key != other.key) return false
        if (oldValue != other.oldValue) return false
        if (newValue != other.newValue) return false

        return true
      }

      override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + oldValue.hashCode()
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
      }

      override fun toString(): String {
        return "Removed(key=$key, oldValue=$oldValue)"
      }
    }
  }
}

/**
 * Folds a flow of [MapEvent]s into a flow of [Map].
 *
 * @param emitPartials When this is `false`, it will not emit the first [Map] until a [Populated]
 *   event is received.
 */
@JvmName("runningFoldToMapMapEvent")
fun <K : Any, V : Any> Flow<MapEvent<K, V>>.runningFoldToMap(
    emitPartials: Boolean = false
): Flow<Map<K, V>> = flow {
  var emitted = false
  var populated = false
  var map = persistentMapOf<K, V>()

  if (emitPartials) emit(map.serializable())

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
        if (!emitted && !emitPartials) emit = true
      }
    }
    if (emit) {
      emitted = true
      emit(map.serializable())
    }
  }
}

/** Folds a flow of [EntryEvent]s into a flow of [Map]. */
@JvmName("runningFoldToMapEntryEvent")
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

/**
 * Conflates each key in the map individually. This buffers emissions, but due to conflation, the
 * maximum number of queued emissions is equal to the number of keys in the map.
 *
 * [Populated] is not sent until all outstanding key value pairs have been published, meaning it is
 * possible to receive [Removed] or multiple [Upsert]s for a single key before the [Populated].
 *
 * Earlier keys are emitted first, so if the upsert events `A=a, B=b, A=aa` are conflated - the
 * emission order will be `A=aa, B=b`.
 */
fun <K : Any, V : Any> Flow<MapEvent<K, V>>.conflateKeys() = channelFlow {
  val upstream = produce { collect { send(it) } }
  var unsentPopulated = false
  val unsentValues = LinkedHashMap<K, EntryEvent<K, V>>()

  fun nextValueToSend(): MapEvent<K, V>? =
      unsentValues.entries.firstOrNull()?.value ?: Populated.takeIf { unsentPopulated }

  whileSelect {
    nextValueToSend()?.let { value ->
      onSend(value) {
        when (value) {
          is EntryEvent -> unsentValues.remove(value.key)
          is Populated -> unsentPopulated = false
        }
        true
      }
    }
    upstream.onReceive { event ->
      when (event) {
        is Populated -> unsentPopulated = true
        is EntryEvent -> {
          val key = event.key
          val oldEvent = unsentValues[key]
          if (oldEvent == null) {
            unsentValues[key] = event // Nothing to conflate
          } else {
            val oldValue = oldEvent.oldValue
            when (event) {
              is Removed -> {
                when (oldEvent) {
                  is Removed -> error("Two Removed events for the same key")
                  is Upsert -> if (oldValue != null) unsentValues[key] = Removed(key, oldValue)
                }
              }

              is Upsert -> {
                when (oldEvent) {
                  is Removed -> unsentValues[key] = Upsert(key, null, event.newValue)
                  is Upsert -> unsentValues[key] = Upsert(key, oldValue, event.newValue)
                }
              }
            }
          }
        }
      }
      true
    }
  }
}
