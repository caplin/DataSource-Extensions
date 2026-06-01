package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Removed
import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import kotlin.Int.Companion.MAX_VALUE
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.SUSPEND
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
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
  fun asMap(): Map<K, V>
}

/** An event emitted by [MapFlow.asFlowWithState]. */
sealed interface FlowMapStreamEvent<out K : Any, out V : Any> {
  /** Emitted on initial collection, containing the entire initial [map] state. */
  @JvmInline
  value class InitialState<K : Any, V : Any>(val map: Map<K, V>) : FlowMapStreamEvent<K, V>

  /** Emitted for subsequent updates, containing only the delta ([event]). */
  @JvmInline
  value class EventUpdate<K : Any, V : Any>(val event: EntryEvent<K, V>) : FlowMapStreamEvent<K, V>

  /** Emitted when the map is cleared. */
  object Cleared : FlowMapStreamEvent<Nothing, Nothing> {
    override fun toString(): String = "Cleared"
  }
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
   *
   * **Note:** This method is useful when individual entry tracking or filtering is required.
   * However, for maps with a large initial state, it has higher overhead because it emits an
   * individual [Upsert] event for every existing entry before emitting [Populated]. If you only
   * need the current map state and subsequent updates, consider using [asFlowWithState] for better
   * performance.
   */
  fun asFlow(predicate: ((K, V) -> Boolean)? = null): Flow<MapEvent<K, V>>

  /**
   * A [Flow] that emits a [FlowMapStreamEvent] to represent the state and its mutations over time.
   *
   * On initial collection, it emits a single [FlowMapStreamEvent.InitialState] with the full
   * initial map state. Later events are emitted as [FlowMapStreamEvent.EventUpdate] containing the
   * [Upsert] or [Removed] deltas.
   *
   * **Note:** This is the preferred method for performance-sensitive subscribers who need the
   * current state immediately, as it avoids the overhead of processing individual events to
   * reconstruct the initial map, while also avoiding the serialization cost of sending the full map
   * on every subsequent update.
   */
  fun asFlowWithState(): Flow<FlowMapStreamEvent<K, V>>

  /**
   * A [Flow] of the latest value for the provided [key] or `null` if no value is present.
   *
   * Events can be conflated with [conflate].
   */
  fun valueFlow(key: K): Flow<V?>
}

/** Folds a flow of [FlowMapStreamEvent]s into a flow of [Map]. */
@JvmName("runningFoldToMapFlowMapStreamEvent")
fun <K : Any, V : Any> Flow<FlowMapStreamEvent<K, V>>.runningFoldToMap(): Flow<Map<K, V>> = flow {
  var map: PersistentMap<K, V>? = null

  collect { streamEvent ->
    when (streamEvent) {
      is FlowMapStreamEvent.InitialState -> {
        map = streamEvent.map.toPersistentMap()
        emit(map!!)
      }

      is FlowMapStreamEvent.Cleared -> {
        map = persistentMapOf()
        emit(map!!)
      }

      is FlowMapStreamEvent.EventUpdate -> {
        val currentMap = map ?: error("InitialState must be received before EventUpdate")
        when (val mapEvent = streamEvent.event) {
          is Removed -> {
            map =
                currentMap.remove(mapEvent.key).also { newMap ->
                  check(newMap !== currentMap) {
                    "Attempted to remove non existent key ${mapEvent.key}"
                  }
                }
            emit(map!!)
          }

          is Upsert -> {
            map = currentMap.put(mapEvent.key, mapEvent.newValue)
            emit(map!!)
          }
        }
      }
    }
  }
}

private class FlowMapImpl<K : Any, V : Any>(initialMap: PersistentMap<K, V>) :
    MutableFlowMap<K, V> {

  private data class FlowMapEvent<K : Any, V : Any>(
      val map: PersistentMap<K, V>,
      val events: List<EntryEvent<K, V>>,
      val isClear: Boolean = false,
  )

  private val writeLock = Any()
  @Volatile private var currentMap: PersistentMap<K, V> = initialMap

  private val signal =
      MutableSharedFlow<FlowMapEvent<K, V>>(1, MAX_VALUE, SUSPEND).apply {
        tryEmit(FlowMapEvent(initialMap, emptyList()))
      }

  override fun asMap(): PersistentMap<K, V> = currentMap

  override fun asFlow(predicate: ((K, V) -> Boolean)?): Flow<MapEvent<K, V>> = flow {
    val emittedKeys = if (predicate != null) mutableSetOf<K>() else null

    var first = true
    signal.collect { flowMapEvent ->
      if (first) {
        val map = flowMapEvent.map
        if (predicate == null) {
          for (entry in map) {
            emit(Upsert(entry.key, null, entry.value))
          }
        } else {
          for (entry in map) {
            val k = entry.key
            val v = entry.value
            if (predicate(k, v)) {
              emittedKeys!!.add(k)
              emit(Upsert(k, null, v))
            }
          }
        }
        emit(Populated)
        first = false
      } else {
        val events = flowMapEvent.events
        if (predicate == null) {
          for (event in events) {
            emit(event)
          }
        } else {
          for (event in events) {
            when (event) {
              is Removed -> {
                if (emittedKeys!!.remove(event.key)) emit(event)
              }

              is Upsert -> {
                val key = event.key
                val newValue = event.newValue
                if (predicate(key, newValue)) {
                  val wasEmitted = !emittedKeys!!.add(key)
                  if (wasEmitted) {
                    emit(event)
                  } else {
                    emit(Upsert(key, null, newValue))
                  }
                } else if (emittedKeys!!.remove(key)) {
                  emit(Removed(key, event.oldValue!!))
                }
              }
            }
          }
        }
      }
    }
  }

  override fun asFlowWithState(): Flow<FlowMapStreamEvent<K, V>> = flow {
    var first = true
    signal.collect { flowMapEvent ->
      if (first) {
        emit(FlowMapStreamEvent.InitialState(flowMapEvent.map))
        first = false
      } else {
        if (flowMapEvent.isClear) {
          emit(FlowMapStreamEvent.Cleared)
        } else {
          val events = flowMapEvent.events
          for (event in events) {
            emit(FlowMapStreamEvent.EventUpdate(event))
          }
        }
      }
    }
  }

  override fun valueFlow(key: K): Flow<V?> =
      flow {
            var first = true
            signal.collect { flowMapEvent ->
              if (first) {
                first = false
                emit(flowMapEvent.map[key])
              } else {
                for (event in flowMapEvent.events) {
                  if (event.key == key) {
                    emit(event.newValue)
                    break
                  }
                }
              }
            }
          }
          .distinctUntilChanged()

  override fun put(key: K, value: V): V? =
      synchronized(writeLock) {
        val cur = currentMap
        val oldValue = cur[key]
        if (oldValue == value) return@synchronized oldValue
        val next = cur.put(key, value)
        currentMap = next
        signal.tryEmit(FlowMapEvent(next, listOf(Upsert(key, oldValue, value))))
        oldValue
      }

  override fun remove(key: K): V? =
      synchronized(writeLock) {
        val cur = currentMap
        val oldValue = cur[key] ?: return@synchronized null
        val next = cur.remove(key)
        currentMap = next
        signal.tryEmit(FlowMapEvent(next, listOf(Removed(key, oldValue))))
        oldValue
      }

  override val entries: Set<Map.Entry<K, V>>
    get() = currentMap.entries

  override val keys: Set<K>
    get() = currentMap.keys

  override val size: Int
    get() = currentMap.size

  override val values: Collection<V>
    get() = currentMap.values

  override fun isEmpty(): Boolean = currentMap.isEmpty()

  override fun get(key: K): V? = currentMap[key]

  override fun containsValue(value: V): Boolean = currentMap.containsValue(value)

  override fun containsKey(key: K): Boolean = currentMap.containsKey(key)

  override fun putAll(from: Map<out K, V>) =
      synchronized(writeLock) {
        val cur = currentMap
        val events =
            from.mapNotNull { (key, newValue) ->
              val oldValue = cur[key]
              if (newValue != oldValue) Upsert(key, oldValue, newValue) else null
            }
        if (events.isEmpty()) return@synchronized
        val next = cur.putAll(from)
        currentMap = next
        signal.tryEmit(FlowMapEvent(next, events))
      }

  override fun clear() =
      synchronized(writeLock) {
        val cur = currentMap
        if (cur.isEmpty()) return@synchronized
        val events = cur.map { Removed(it.key, it.value) }
        val next = cur.clear()
        currentMap = next
        signal.tryEmit(FlowMapEvent(next, events, isClear = true))
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
