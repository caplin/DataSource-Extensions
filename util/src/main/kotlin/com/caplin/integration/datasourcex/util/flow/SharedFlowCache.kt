package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.job

/**
 * A cache of [SharedFlow]s keyed by [K], sharing a single upstream collection per key across that
 * key's subscribers. Create one with [sharedFlowCache], or [completingSharedFlowCache] for
 * completion/error propagation.
 *
 * A key is created on first [get] and evicted once its upstream ends - it completes or errors, or
 * (under a stopping `started` such as [SharingStarted.WhileSubscribed]) its subscribers have all
 * gone - so the cache cannot grow without bound. Eviction tears down the key's sharing coroutine.
 *
 * This exposes a plain [SharedFlow], so subscribers see values only: the upstream's completion is
 * not delivered to them, and neither is an unhandled upstream error - it fails the key's own
 * sharing coroutine in isolation (the entry is evicted; the cache scope and other keys are
 * unaffected) and reaches the scope's `CoroutineExceptionHandler`. Handle errors in the supplier
 * (e.g. retry or catch). For completion/error propagation to subscribers, use
 * [completingSharedFlowCache].
 *
 * [get] resolves the entry lazily, so subscribe to the returned flow promptly: a flow retained
 * across its key's eviction will not restart the upstream.
 */
class SharedFlowCache<K : Any, V>
internal constructor(
    private val scope: CoroutineScope,
    private val started: SharingStarted,
    private val replay: Int,
    private val evictWhen: ((V) -> Boolean)? = null,
) {
  private val cache = ConcurrentHashMap<K, SharedFlow<V>>()

  /** The shared flow for [key], creating it from [supplier] on first access. */
  operator fun get(key: K, supplier: (K) -> Flow<V>): SharedFlow<V> =
      cache.computeIfAbsent(key) { k -> share(k, supplier) }

  private fun share(key: K, supplier: (K) -> Flow<V>): SharedFlow<V> {
    val entryScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job))
    val evictWhen = evictWhen
    lateinit var shared: SharedFlow<V>
    val upstream =
        if (evictWhen == null) supplier(key)
        else
            supplier(key).transformWhile {
              // Evict before publishing a terminal value, so a re-get racing the completion misses
              // and rebuilds rather than re-reading the terminal, then stop the upstream.
              val terminal = evictWhen(it)
              if (terminal) cache.remove(key, shared)
              emit(it)
              !terminal
            }
    shared =
        upstream
            // Drop the entry (if still ours) and tear the sharing coroutine down once the upstream
            // ends: it completed/errored, or `started` cancelled it after the subscribers left.
            .onCompletion {
              cache.remove(key, shared)
              entryScope.cancel()
            }
            .shareIn(entryScope, started, replay)
    return shared
  }
}

/**
 * Creates a [SharedFlowCache] - a keyed cache of plain [SharedFlow]s, one shared upstream per key,
 * each evicted once its upstream ends (see [SharedFlowCache]).
 *
 * @param scope parent scope; each key's sharing coroutine is a child of it.
 * @param started the [SharingStarted] strategy applied to each key's upstream.
 * @param replay number of values replayed to new subscribers of a key.
 */
fun <K : Any, V> sharedFlowCache(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(),
    replay: Int = 0,
): SharedFlowCache<K, V> = SharedFlowCache(scope, started, replay)

/**
 * Creates a [CompletingSharedFlowCache] backed by a [SharedFlowCache]: each supplier's terminal
 * completion/error is materialised into the shared stream and rematerialised per subscriber, so -
 * unlike a plain [SharedFlow] - completion and errors propagate downstream. The entry is evicted as
 * the terminal is published, so a downstream `retry` re-resolves to a fresh entry (re-running the
 * supplier) rather than re-reading the terminal.
 *
 * @param scope parent scope; each key's sharing coroutine is a child of it.
 * @param started the [SharingStarted] strategy applied to each key's upstream.
 * @param replay replayed values; use `>= 1` so a subscriber arriving after completion still
 *   observes the terminal.
 */
@OptIn(DelicateCoroutinesApi::class)
fun <K : Any, T> completingSharedFlowCache(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(),
    replay: Int = 0,
): CompletingSharedFlowCache<K, T> {
  val cache = SharedFlowCache<K, Any?>(scope, started, replay) { it is Completion }
  return CompletingSharedFlowCache { key, supplier ->
    InternalCompletingSharedFlow { cache.get(key) { k -> supplier(k).materializeUnboxed() } }
  }
}
