package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Insert
import com.caplin.integration.datasourcex.util.flow.SetEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.SetEvent.Populated
import com.caplin.integration.datasourcex.util.serializable
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

sealed interface SetEvent<out V : Any> : Serializable {

  object Populated : SetEvent<Nothing> {
    private fun readResolve(): Any = Populated

    override fun toString(): String {
      return "Populated()"
    }
  }

  sealed interface EntryEvent<out V : Any> : SetEvent<V> {
    val value: V

    operator fun component1(): V = value

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
    relaxed: Boolean = false
) = flow {
  var emitted = false
  var populated = false
  var set = persistentSetOf<V>()

  if (emitPartials) emit(set.serializable())

  collect { setEvent ->
    var emit = false
    val oldSet = set
    when (setEvent) {
      is Removed -> {
        set = oldSet.remove(setEvent.value)
        val changed = oldSet !== set
        if (relaxed && !changed) error("Received $setEvent but this did not exist")
        if (changed && (populated || emitPartials)) emit = true
      }

      is Insert -> {
        set = oldSet.add(setEvent.value)
        val changed = oldSet !== set
        if (relaxed && !changed) error("Received $setEvent but this already existed")
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
      emit(set.serializable())
    }
  }
}

@JvmName("flatMapLatestAndMergeSet")
fun <V : Any, R> Flow<Set<V>>.flatMapLatestAndMerge(
    entryEventTransformer: (EntryEvent<V>) -> Flow<R>
): Flow<R> = toEvents().flatMapLatestAndMerge(entryEventTransformer)

fun <V : Any, R> Flow<SetEvent<V>>.flatMapLatestAndMerge(
    entryEventTransformer: (EntryEvent<V>) -> Flow<R>
) = channelFlow {
  val jobs = ConcurrentHashMap<V, Job>()
  collect { setEvent ->
    when (setEvent) {
      is EntryEvent<V> -> {
        jobs[setEvent.value]?.cancelAndJoin()
        jobs[setEvent.value] =
            entryEventTransformer(setEvent)
                .onEach { send(it) }
                .onCompletion { throwable -> if (throwable == null) jobs.remove(setEvent.value) }
                .launchIn(this@channelFlow)
      }

      is Populated -> {}
    }
  }
}
