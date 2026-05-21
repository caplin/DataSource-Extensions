package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.MapEvent.EntryEvent.Upsert
import com.caplin.integration.datasourcex.util.flow.MapEvent.Populated
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup

/**
 * Benchmarks for [FlowMap] implementation, focusing on mutation performance, lookup efficiency, and
 * Flow-based state reconstruction.
 *
 * Latest measurement (Temurin 17, single fork, Windows 11):
 *
 * | Benchmark                                          | Score (ops/ms) | Error      |
 * |----------------------------------------------------|---------------:|-----------:|
 * | asFlowCollection                                   |         42.548 |    ± 3.394 |
 * | asFlowWithPredicateCollection                      |         34.197 |    ± 0.905 |
 * | asFlowWithStateCollection                          |       2070.676 |   ± 62.405 |
 * | concurrentPutCycling (4 threads)                   |       2664.437 |  ± 354.958 |
 * | getFromLargeMap                                    |      94992.879 | ± 8888.292 |
 * | putAllLarge                                        |        321.358 |   ± 14.364 |
 * | putAllSmall                                        |      22155.471 | ± 4211.902 |
 * | putAndRemove                                       |      10372.256 |  ± 171.209 |
 * | putChanging                                        |      16413.289 | ± 1372.233 |
 * | putChangingWithDrainingSubscribers, n=1            |        341.278 |   ± 18.641 |
 * | putChangingWithDrainingSubscribers, n=10           |         60.685 |    ± 4.373 |
 * | putChangingWithDrainingSubscribers, n=100          |          5.984 |    ± 1.017 |
 * | putChangingWithDrainingValueFlowSubscribers, n=1   |        218.861 |   ± 30.982 |
 * | putChangingWithDrainingValueFlowSubscribers, n=10  |         61.384 |    ± 6.342 |
 * | putChangingWithDrainingValueFlowSubscribers, n=100 |          6.295 |    ± 0.777 |
 * | putChangingWithSubscribers, n=1                    |       3921.719 |  ± 269.207 |
 * | putChangingWithSubscribers, n=10                   |         65.148 |    ± 7.733 |
 * | putChangingWithSubscribers, n=100                  |        530.386 |  ± 168.633 |
 * | putCycling                                         |      10580.715 | ± 2106.702 |
 * | putSingle                                          |      63821.667 | ± 1333.707 |
 * | putWithSubscribers, n=1                            |      63339.972 | ± 2133.787 |
 * | putWithSubscribers, n=10                           |      63061.449 | ± 1313.094 |
 * | putWithSubscribers, n=100                          |      63960.063 | ± 3961.219 |
 * | toFlowMapInBenchmark                               |         27.103 |    ± 2.115 |
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

  /** Per-thread state for benchmarks that need an ever-changing value. */
  @State(Scope.Thread)
  open class ChangingValueState {
    var counter: Int = 0
  }

  /** Per-thread state for benchmarks that cycle through a fixed key pool. */
  @State(Scope.Thread)
  open class KeyCyclingState {
    val keys: List<String> = (0..255).map { "key$it" }
    var idx: Int = 0
  }

  /**
   * Measures the throughput of [MutableFlowMap.put] when the value actually changes on every call.
   * Unlike [putSingle] (which puts the same key/value repeatedly and short-circuits on the equality
   * check) this exercises the full mutate-and-emit path.
   */
  @Benchmark
  fun putChanging(value: ChangingValueState) {
    flowMap.put("key", "v${value.counter++}")
  }

  /**
   * Measures the throughput of [MutableFlowMap.put] rotating through a 256-entry key pool with a
   * fresh value every time. Every put is a real mutation against different parts of the persistent
   * map's trie.
   */
  @Benchmark
  fun putCycling(state: KeyCyclingState) {
    val key = state.keys[state.idx and 255]
    flowMap.put(key, "v${state.idx}")
    state.idx++
  }

  /**
   * Same workload as [putCycling] but with 4 contending writer threads, measuring the cost of the
   * write-side synchronization under contention.
   */
  @Benchmark
  @Threads(4)
  fun concurrentPutCycling(state: KeyCyclingState) {
    val key = state.keys[state.idx and 255]
    flowMap.put(key, "v${state.idx}")
    state.idx++
  }

  /** Holds two pre-built large maps so that successive [MutableFlowMap.putAll] calls all mutate. */
  @State(Scope.Benchmark)
  open class LargePutAllState {
    val mapA: Map<String, String> = (0..99).associate { "key$it" to "A$it" }
    val mapB: Map<String, String> = (0..99).associate { "key$it" to "B$it" }
  }

  /** Per-thread toggle so each [putAllLarge] invocation alternates between the two large maps. */
  @State(Scope.Thread)
  open class PutAllToggleState {
    var useA: Boolean = false
  }

  /**
   * Measures the throughput of [MutableFlowMap.putAll] with a 100-entry map where every entry's
   * value differs from the current contents. Unlike [putAllSmall] (3 entries, no-op after the first
   * iteration), this exercises the full event-list construction and emission path.
   */
  @Benchmark
  fun putAllLarge(shared: LargePutAllState, toggle: PutAllToggleState) {
    flowMap.putAll(if (toggle.useA) shared.mapA else shared.mapB)
    toggle.useA = !toggle.useA
  }

  /**
   * Measures put throughput with active [MutableFlowMap.asFlow] subscribers and a value that
   * actually changes each call - the realistic combination of mutation cost and subscriber fan-out.
   *
   * Note: writer-only throughput. Events may accumulate in the signal's buffer; this does not wait
   * for subscribers to consume them. See [putChangingWithDrainingSubscribers] for end-to-end.
   */
  @Benchmark
  fun putChangingWithSubscribers(state: ActiveSubscriberState, value: ChangingValueState) {
    state.flowMap.put("key", "v${value.counter++}")
  }

  /**
   * State holder for [putChangingWithDrainingSubscribers]. Subscribers bump an [AtomicLong] counter
   * on each consumed event so the writer can spin-wait until every subscriber has observed the
   * latest put. Uses a non-blocking counter rather than a blocking gate so subscriber coroutines
   * never park their dispatcher worker thread.
   */
  @State(Scope.Benchmark)
  open class DrainingSubscriberState {
    @Param("1", "10", "100") var subscriberCount: Int = 0

    lateinit var flowMap: MutableFlowMap<String, String>
    lateinit var scope: CoroutineScope
    val consumed: AtomicLong = AtomicLong()

    @Setup
    fun setup() {
      flowMap = mutableFlowMapOf()
      scope = CoroutineScope(Dispatchers.Default)
      val attached = CountDownLatch(subscriberCount)
      repeat(subscriberCount) {
        flowMap
            .asFlow()
            .onEach { event ->
              if (event === Populated) attached.countDown() else consumed.incrementAndGet()
            }
            .launchIn(scope)
      }
      // Ensure every subscriber is actually attached (has seen its initial Populated) before
      // the first measurement, otherwise the writer would wait for events the subscriber never
      // received.
      attached.await()
    }

    @TearDown
    fun tearDown() {
      scope.cancel()
    }
  }

  /**
   * Like [putChangingWithSubscribers] but blocks after each put until every subscriber has run its
   * `onEach` for the emitted event. Measures end-to-end put-to-consume throughput rather than the
   * writer's buffer-and-go throughput.
   */
  @Benchmark
  fun putChangingWithDrainingSubscribers(
      state: DrainingSubscriberState,
      value: ChangingValueState,
  ) {
    val target = state.consumed.get() + state.subscriberCount
    state.flowMap.put("key", "v${value.counter++}")
    while (state.consumed.get() < target) {
      Thread.onSpinWait()
    }
  }

  /**
   * State holder for [putChangingWithDrainingValueFlowSubscribers]. Each subscriber watches a
   * distinct key; the benchmark cycles the writer through those keys so exactly one subscriber
   * emits per put, allowing the writer to spin-wait on a single counter increment. The map is
   * pre-populated with 1,000 filler entries so the per-event filter cost on the non-matching N-1
   * subscribers is non-trivial.
   */
  @State(Scope.Benchmark)
  open class DrainingValueFlowSubscriberState {
    @Param("1", "10", "100") var subscriberCount: Int = 0

    lateinit var flowMap: MutableFlowMap<String, String>
    lateinit var scope: CoroutineScope
    lateinit var keys: List<String>
    val consumed: AtomicLong = AtomicLong()

    @Setup
    fun setup() {
      flowMap = mutableFlowMapOf()
      repeat(1000) { flowMap.put("filler$it", "v$it") }
      keys = (0 until subscriberCount).map { "watched$it" }
      // Seed each watched key so the first per-iteration put can't dedup against null.
      keys.forEach { flowMap.put(it, "init") }

      scope = CoroutineScope(Dispatchers.Default)
      val attached = CountDownLatch(subscriberCount)
      repeat(subscriberCount) { i ->
        flowMap
            .valueFlow(keys[i])
            .onEach { if (attached.count > 0) attached.countDown() else consumed.incrementAndGet() }
            .launchIn(scope)
      }
      attached.await()
    }

    @TearDown
    fun tearDown() {
      scope.cancel()
    }
  }

  /**
   * Measures put-to-consume throughput with N active [FlowMap.valueFlow] subscribers each watching
   * a distinct key. The writer cycles through those keys so each put matches exactly one
   * subscriber; the other N-1 subscribers exercise the per-event filter path (key compare under the
   * new impl, `map[key]` lookup under the old) but never emit. End-to-end throughput captures both
   * the writer's fan-out cost and the matching subscriber's emit-chain time.
   */
  @Benchmark
  fun putChangingWithDrainingValueFlowSubscribers(
      state: DrainingValueFlowSubscriberState,
      value: ChangingValueState,
  ) {
    val idx = value.counter++
    val key = state.keys[idx % state.subscriberCount]
    val target = state.consumed.get() + 1
    state.flowMap.put(key, "v$idx")
    while (state.consumed.get() < target) {
      Thread.onSpinWait()
    }
  }
}
