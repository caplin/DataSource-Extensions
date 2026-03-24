package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*

/**
 * Benchmarks for [FlowMap] implementation, focusing on mutation performance, lookup efficiency, and
 * Flow-based state reconstruction.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class FlowMapBenchmark {

  private lateinit var flowMap: MutableFlowMap<String, String>

  @Setup
  fun setup() {
    flowMap = mutableFlowMapOf()
  }

  /**
   * Measures the throughput of single [MutableFlowMap.put] operations on an initially empty map.
   */
  @Benchmark
  fun putSingle() {
    flowMap.put("key", "value")
  }

  /**
   * Measures the throughput of a [MutableFlowMap.put] followed by a [MutableFlowMap.remove] on the
   * same key, exercising the event emission and state update logic.
   */
  @Benchmark
  fun putAndRemove() {
    flowMap.put("key", "value")
    flowMap.remove("key")
  }

  /**
   * Measures the throughput of [MutableFlowMap.putAll] with a small map, which triggers multiple
   * events in a single state update.
   */
  @Benchmark
  fun putAllSmall() {
    flowMap.putAll(mapOf("1" to "A", "2" to "B", "3" to "C"))
  }

  /** State holder for a [FlowMap] pre-populated with 1,000 entries. */
  @State(Scope.Benchmark)
  open class PopulatedFlowMapState {
    lateinit var flowMap: MutableFlowMap<String, Int>

    @Setup
    fun setup() {
      flowMap = mutableFlowMapOf()
      repeat(1000) { flowMap.put("key$it", it) }
    }
  }

  /** State holder for measuring mutation throughput with multiple active subscribers. */
  @State(Scope.Benchmark)
  open class ActiveSubscriberState {
    @Param("1", "10", "100") var subscriberCount: Int = 0

    lateinit var flowMap: MutableFlowMap<String, String>
    lateinit var scope: CoroutineScope

    @Setup
    fun setup() {
      flowMap = mutableFlowMapOf()
      // Using Dispatchers.Default for subscribers to simulate real-world processing
      scope = CoroutineScope(Dispatchers.Default)
      repeat(subscriberCount) { flowMap.asFlow().launchIn(scope) }
    }

    @TearDown
    fun tearDown() {
      scope.cancel()
    }
  }

  /**
   * Measures the throughput of [MutableFlowMap.put] when there are multiple active subscribers
   * collecting from the map. This identifies contention or overhead in the event dispatching logic.
   */
  @Benchmark
  fun putWithSubscribers(state: ActiveSubscriberState) {
    state.flowMap.put("key", "value")
  }

  /** Measures the throughput of retrieving a value from a large, pre-populated [FlowMap]. */
  @Benchmark
  fun getFromLargeMap(state: PopulatedFlowMapState): Int? {
    return state.flowMap["key500"]
  }

  /**
   * Measures the time taken to collect the initial state of a large [FlowMap] via [FlowMap.asFlow].
   */
  @Benchmark
  fun asFlowCollection(state: PopulatedFlowMapState) = runBlocking {
    state.flowMap
        .asFlow()
        .takeWhile { it != Populated }
        .collect {
          // just collect
        }
  }

  /**
   * Measures the time taken to collect the initial state of a large [FlowMap] via
   * [FlowMap.asFlowWithState]. This avoids emitting individual Upsert events, making it much
   * faster.
   */
  @Benchmark
  fun asFlowWithStateCollection(state: PopulatedFlowMapState) = runBlocking {
    state.flowMap.asFlowWithState().take(1).collect {
      // just collect
    }
  }

  /**
   * Measures the time taken to collect the initial state of a large [FlowMap] via [FlowMap.asFlow]
   * when a predicate is applied, exercising the filtering logic within the flow.
   */
  @Benchmark
  fun asFlowWithPredicateCollection(state: PopulatedFlowMapState) = runBlocking {
    state.flowMap
        .asFlow { _, value -> value % 2 == 0 }
        .takeWhile { it != Populated }
        .collect {
          // just collect
        }
  }

  /**
   * Measures the overhead of reconstructing a [FlowMap] from a stream of events using
   * [toFlowMapIn].
   */
  @Benchmark
  fun toFlowMapInBenchmark() = runBlocking {
    val events = flow {
      repeat(100) { emit(Upsert("key$it", null, it)) }
      emit(Populated)
    }
    val scope = CoroutineScope(Dispatchers.Default)
    events.toFlowMapIn(scope)
    scope.cancel()
  }
}
