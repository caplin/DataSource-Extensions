package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Insert
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.SetEvent.Populated
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Events representing a mutation to a [Set]. */
sealed interface SetEvent<out V : Any> {

  /**
   * Indicates that a consistent view of the set has been emitted and only updates will be seen from
   * now on.
   */
  object Populated : SetEvent<Nothing> {
    override fun toString(): String {
      return "Populated()"
    }
  }

  /** Mutation event for a specific entry. */
  sealed interface EntryEvent<out V : Any> : SetEvent<V> {
    val value: V

    operator fun component1(): V = value

    /** An event indicating a value has been inserted into the set. */
    class Insert<out V : Any>(override val value: V) : EntryEvent<V> {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Insert<*>

        return value == other.value
      }

      override fun hashCode(): Int {
        return value.hashCode()
      }

      override fun toString(): String {
        return "Insert(value=$value)"
      }
    }

    /** An event indicating a value has been removed from the set. */
    class Removed<out V : Any>(override val value: V) : EntryEvent<V> {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Removed<*>

        return value == other.value
      }

      override fun hashCode(): Int {
        return value.hashCode()
      }

      override fun toString(): String {
        return "Removed(value=$value)"
      }
    }
  }
}

/**
 * Converts a [Flow] of [Set] to a stream of events representing the changes between consecutive
 * sets.
 *
 * @param V the type of elements in the set
 * @return a [Flow] of events representing the changes between consecutive sets
 */
fun <V : Any> Flow<Set<V>>.toEvents() = flow {
  var previousSet: Set<V>? = null
  collect { newSet ->
    val localPreviousSet = previousSet
    if (localPreviousSet == null) {
      (newSet).forEach { emit(Insert(it)) }
      emit(Populated)
    } else {
      (localPreviousSet - newSet).forEach { emit(Removed(it)) }
      (newSet - localPreviousSet).forEach { emit(Insert(it)) }
    }
    previousSet = newSet
  }
}

/**
 * This method takes a flow of [SetEvent] and transforms it into a flow of [Set]. It uses a running
 * fold operation to maintain the state of the set and emit the updated set whenever a relevant
 * event is received.
 *
 * @param emitPartials if set to `true`, an initial empty set will be emitted, followed by a set for
 *   each received [EntryEvent] prior to the [Populated] event being received. If set to `false`, it
 *   will only emit the current set on the initial [Populated] and each following [EntryEvent].
 * @param relaxed if set to `false`, it will throw an [IllegalStateException] in the following
 *   cases:
 * - If a [Removed] event is received for a value which does not exist in the current set.
 * - If an [Insert] event is received for a value which already exists in the current set.
 *
 * @return a flow of [Set] representing the updated state of the set after each event is received.
 */
fun <V : Any> Flow<SetEvent<V>>.runningFoldToSet(
    emitPartials: Boolean = false,
    relaxed: Boolean = false,
) = flow {
  var emitted = false
  var populated = false
  var set = persistentSetOf<V>()

  if (emitPartials) emit(set)

  collect { setEvent ->
    var emit = false
    val oldSet = set
    when (setEvent) {
      is Removed -> {
        set = oldSet.remove(setEvent.value)
        val changed = oldSet !== set
        if (!relaxed && !changed) error("Received $setEvent but this did not exist")
        if (changed && (populated || emitPartials)) emit = true
      }

      is Insert -> {
        set = oldSet.add(setEvent.value)
        val changed = oldSet !== set
        if (!relaxed && !changed) error("Received $setEvent but this already existed")
        if (changed && (populated || emitPartials)) emit = true
      }

      is Populated -> {
        if (populated) error("Populated event already received")
        populated = true
        if (!emitted && !emitPartials) emit = true
      }
    }
    if (emit) {
      emitted = true
      emit(set)
    }
  }
}
