package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import java.util.TreeMap
import kotlin.Int.Companion.MAX_VALUE
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.SUSPEND
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex

/** Create a new, empty, [MutableFlowMap]. */
fun <K : Any, V : Any> mutableFlowMapOf(): MutableFlowMap<K, V> = FlowMapImpl(persistentMapOf())

/** Create a new [MutableFlowMap], with the specified contents. */
fun <K : Any, V : Any> mutableFlowMapOf(vararg pairs: Pair<K, V>): MutableFlowMap<K, V> =
    FlowMapImpl(persistentMapOf(*pairs))

/** Creates a copy of the provided map as a new [MutableFlowMap]. */
fun <K : Any, V : Any> Map<K, V>.toMutableFlowMap(): MutableFlowMap<K, V> =
    FlowMapImpl(toPersistentMap())

/**
 * Modifiable version of [FlowMap]
 *
 * @see MapFlow
 * @see FlowMap
 * @see mutableFlowMapOf
 * @see toMutableFlowMap
 */
interface MutableFlowMap<K : Any, V : Any> : FlowMap<K, V> {

  /**
   * Associates the specified [value] with the specified [key] in the map.
   *
   * @return the previous value associated with the key, or `null` if the key was not present in the
   *   map.
   */
  fun put(key: K, value: V): V?

  operator fun set(key: K, value: V): V? = put(key, value)

  /**
   * Removes the specified key and its corresponding value from this map.
   *
   * @return the previous value associated with the key, or `null` if the key was not present in the
   *   map.
   */
  fun remove(key: K): V?

  operator fun minusAssign(key: K) {
    remove(key)
  }

  /** Updates this map with key/value pairs from the specified map [from]. */
  fun putAll(from: Map<out K, V>)

  /** Removes all elements from this map. */
  fun clear()
}

/**
 * Exposes the current state and future mutations to specific keys (via [valueFlow]), or the map as
 * a whole (via [asFlow]).
 *
 * @see MapFlow
 * @see mutableFlowMapOf
 * @see toMutableFlowMap
 */
interface FlowMap<K : Any, V : Any> : MapFlow<K, V>, Map<K, V> {

  /** A copy of the map at this point in time */
  fun asMap(): PersistentMap<K, V>
}

interface MapFlow<K : Any, V : Any> {
  /**
   * A [Flow] of events that can be used to reconstitute the current and future state of the
   * [FlowMap].
   *
   * A [predicate] can be provided to filter the contents of the map. [Upsert] will have a `null`
   * [Upsert.oldValue] if there was an underlying value that did not previously match, but now does.
   * A [Removed] will be sent if an entry used to match, but no longer does.
   *
   * Events can be conflated with [conflateKeys].
   */
  fun asFlow(predicate: ((K, V) -> Boolean)? = null): Flow<MapEvent<K, V>>

  /**
   * A [Flow] of the latest value for the provided [key] or `null` if no value is present.
   *
   * Events can be conflated with [conflate].
   */
  fun valueFlow(key: K): Flow<V?>
}

private class FlowMapImpl<K : Any, V : Any>(initialMap: PersistentMap<K, V>) :
    MutableFlowMap<K, V> {
  private data class State<K, V>(val version: Long, val map: PersistentMap<K, V>)

  private data class FlowMapEvent<K : Any, V : Any>(
      val state: State<K, V>,
      val events: List<EntryEvent<K, V>>
  )

  private val state = MutableStateFlow(State(0L, initialMap))

  private val signal =
      MutableSharedFlow<FlowMapEvent<K, V>>(1, MAX_VALUE, SUSPEND).apply {
        tryEmit(FlowMapEvent(state.value, emptyList()))
      }

  private val orderedSignal = flow {
    var version = -1L
    var held: TreeMap<Long, FlowMapEvent<K, V>>? = null
    signal.collect {
      val expectedVersion = version + 1
      if (version == -1L || it.state.version == expectedVersion) {
        version = it.state.version
        emit(it)
        if (held != null) {
          var i = 1
          do {
            val next = held?.remove(expectedVersion + i++)
            if (next != null) emit(next)
          } while (next != null && held?.isNotEmpty() == true)
          if (held?.isEmpty() == true) held = null
        }
      } else if (it.state.version > expectedVersion) {
        held = held ?: TreeMap()
        held?.put(it.state.version, it)
      }
    }
  }

  override fun asMap(): PersistentMap<K, V> = state.value.map

  override fun asFlow(predicate: ((K, V) -> Boolean)?): Flow<MapEvent<K, V>> = flow {
    val emittedKeys = if (predicate != null) mutableSetOf<K>() else null

    suspend fun processEvents(mapEvents: List<MapEvent<K, V>>) {
      mapEvents.forEach { mapEvent ->
        if (emittedKeys == null || predicate == null) emit(mapEvent)
        else
            when (mapEvent) {
              is Removed -> if (emittedKeys.remove(mapEvent.key)) emit(mapEvent)
              is Upsert ->
                  if (predicate(mapEvent.key, mapEvent.newValue)) {
                    val newValue =
                        if (emittedKeys.add(mapEvent.key))
                            Upsert(mapEvent.key, null, mapEvent.newValue)
                        else mapEvent
                    emit(newValue)
                  } else if (emittedKeys.remove(mapEvent.key))
                      emit(Removed(mapEvent.key, mapEvent.oldValue!!))

              else -> {}
            }
      }
    }

    var first = true
    orderedSignal.filterNotNull().collect { flowMapEvent ->
      if (first) {
        flowMapEvent.state.map.entries
            .map { Upsert(it.key, null, it.value) }
            .let { processEvents(it) }
        emit(Populated)
        first = false
      } else processEvents(flowMapEvent.events)
    }
  }

  override fun valueFlow(key: K): Flow<V?> = state.map { it.map[key] }.distinctUntilChanged()

  override fun put(key: K, value: V): V? {
    val (prev, next) =
        state.updateAndGetPrevAndNext {
          if (it.map[key] == value) it else State(it.version + 1, it.map.put(key, value))
        }

    val oldValue = prev.map[key]
    if (prev != next) signal.tryEmit(FlowMapEvent(next, listOf(Upsert(key, oldValue, value))))
    return oldValue
  }

  override fun remove(key: K): V? {
    val (prev, next) =
        state.updateAndGetPrevAndNext {
          if (it.map[key] == null) it else State(it.version + 1, it.map.remove(key))
        }

    val oldValue = prev.map[key]
    if (oldValue != null) signal.tryEmit(FlowMapEvent(next, listOf(Removed(key, oldValue))))
    return oldValue
  }

  override val entries: Set<Map.Entry<K, V>>
    get() = state.value.map.entries

  override val keys: Set<K>
    get() = state.value.map.keys

  override val size: Int
    get() = state.value.map.size

  override val values: Collection<V>
    get() = state.value.map.values

  override fun isEmpty(): Boolean = state.value.map.isEmpty()

  override fun get(key: K): V? = state.value.map[key]

  override fun containsValue(value: V): Boolean = state.value.map.containsValue(value)

  override fun containsKey(key: K): Boolean = state.value.map.containsKey(key)

  override fun putAll(from: Map<out K, V>) {
    val (prev, next) = state.updateAndGetPrevAndNext { State(it.version + 1, it.map.putAll(from)) }

    val events =
        from.mapNotNull { (key, newValue) ->
          val oldValue = prev.map[key]
          if (newValue != oldValue) Upsert(key, oldValue, newValue) else null
        }
    if (events.isNotEmpty()) signal.tryEmit(FlowMapEvent(next, events))
  }

  override fun clear() {
    val (prev, next) =
        state.updateAndGetPrevAndNext {
          if (it.map.isEmpty()) it else State(it.version + 1, it.map.clear())
        }
    if (prev.map.isNotEmpty())
        signal.tryEmit(FlowMapEvent(next, prev.map.map { Removed(it.key, it.value) }))
  }
}

suspend fun <K : Any, V : Any> Flow<MapEvent<K, V>>.toFlowMapIn(
    scope: CoroutineScope
): FlowMap<K, V> {
  val flowMap = mutableFlowMapOf<K, V>()
  val populated = Mutex(true)
  onEach {
        when (it) {
          is Upsert -> flowMap.put(it.key, it.newValue)
          is Removed -> flowMap.remove(it.key)
          Populated -> populated.unlock()
        }
      }
      .launchIn(scope)
  populated.lock()
  return flowMap
}

@JvmName("simpleToFlowMapIn")
suspend fun <K : Any, V : Any> Flow<SimpleMapEvent<K, V>>.toFlowMapIn(
    scope: CoroutineScope
): FlowMap<K, V> {
  val flowMap = mutableFlowMapOf<K, V>()
  val populated = Mutex(true)
  onEach {
        when (it) {
          is SimpleMapEvent.EntryEvent.Upsert -> flowMap.put(it.key, it.newValue)
          is SimpleMapEvent.EntryEvent.Removed -> flowMap.remove(it.key)
          SimpleMapEvent.Populated -> populated.unlock()
        }
      }
      .launchIn(scope)
  populated.lock()
  return flowMap
}

internal inline fun <T> MutableStateFlow<T>.updateAndGetPrevAndNext(
    function: (T) -> T
): Pair<T, T> {
  while (true) {
    val prevValue = value
    val nextValue = function(prevValue)
    if (compareAndSet(prevValue, nextValue)) return prevValue to nextValue
  }
}
