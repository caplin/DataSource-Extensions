@file:OptIn(ObsoleteCoroutinesApi::class, DelicateCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.flow.CompletingSharedFlowCache.Companion.MapActorAction.Fetch
import com.caplin.integration.datasourcex.util.flow.CompletingSharedFlowCache.Companion.MapActorAction.Reset
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile

/**
 * Similar to [SharedFlow], but completion and error events are also propagated to the downstream
 * subscribers.
 *
 * Created using [shareInCompleting] or [MutableCompletingSharedFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class) sealed class CompletingSharedFlow<T> : AbstractFlow<T>()

/**
 * Alternate version of [CompletingSharedFlowCache] that uses a single supplier, defined up front,
 * for all calls to [get].
 *
 * @see loading
 * @see CompletingSharedFlowCache
 */
fun interface LoadingCompletingSharedFlowCache<K : Any, T : Any?> {

  /**
   * If the requested key [K] is not found in the cache when the [CompletingSharedFlow] returned by
   * [get] is subscribed to, then the supplier provided to the [loading] function will be invoked.
   *
   * @see CompletingSharedFlowCache.get
   */
  operator fun get(key: K): CompletingSharedFlow<T>
}

/** Converts this [CompletingSharedFlowCache] to a [LoadingCompletingSharedFlowCache]. */
fun <K : Any, T : Any?> CompletingSharedFlowCache<K, T>.loading(
    supplier: (K) -> Flow<T>
): LoadingCompletingSharedFlowCache<K, T> = LoadingCompletingSharedFlowCache { key ->
  get(key, supplier)
}

/**
 * A cache of flows which each follow the behaviour of [shareInCompleting] - i.e. they share a
 * single upstream and pass completion and errors on to the downstream.
 */
fun interface CompletingSharedFlowCache<K : Any, T : Any?> {

  /**
   * If the requested key [K] is not found in the cache when the [CompletingSharedFlow] returned by
   * [get] is subscribed to, then the [supplier] function will be invoked. Any errors throw in
   * [supplier] are thrown as error events within the flow.
   */
  operator fun get(key: K, supplier: (K) -> Flow<T>): CompletingSharedFlow<T>

  companion object {

    private interface MapActorAction<K : Any, T : Any?> {

      val key: K

      data class Fetch<K : Any, T : Any?>(
          override val key: K,
          val supplier: (K) -> Flow<T>,
          val response: CompletableDeferred<SharedFlow<Any?>> = CompletableDeferred(),
      ) : MapActorAction<K, T>

      data class Reset<K : Any, T : Any?>(override val key: K) : MapActorAction<K, T>
    }

    operator fun <K : Any, T : Any?> invoke(
        scope: CoroutineScope,
        started: SharingStarted,
        replay: Int,
    ): CompletingSharedFlowCache<K, T> {
      val actor =
          scope.actor<MapActorAction<K, T>> {
            val map = mutableMapOf<K, SharedFlow<Any?>>()

            for (item in channel) {
              when (item) {
                is Fetch ->
                    item.response.complete(
                        map.getOrPut(item.key) {
                          item
                              .supplier(item.key)
                              .materializeUnboxed()
                              .transformWhile {
                                emit(it)
                                it !is Completion
                              }
                              .onCompletion { channel.send(Reset(item.key)) }
                              .shareIn(scope, started, replay)
                        })

                is Reset -> map.remove(item.key)
              }
            }
          }

      return CompletingSharedFlowCache { key, supplier ->
        val fetch = Fetch(key, supplier)
        InternalCompletingSharedFlow {
          actor.send(fetch)
          fetch.response.await()
        }
      }
    }
  }
}

/**
 * [CompletingSharedFlow] equivalent of [MutableSharedFlow], providing [emit] and [complete]
 * functionality.
 */
class MutableCompletingSharedFlow<T>
internal constructor(
    private val mutableSharedFlow: MutableSharedFlow<Any?>,
    internal val internalCompletingSharedFlow: InternalCompletingSharedFlow<T> =
        InternalCompletingSharedFlow {
          mutableSharedFlow
        },
) : CompletingSharedFlow<T>(), FlowCollector<T> by mutableSharedFlow {

  @Suppress("unused")
  constructor(
      replay: Int = 0,
      extraBufferCapacity: Int = 0,
      onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
  ) : this(MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow))

  @Suppress("unused") fun tryEmit(value: T): Boolean = mutableSharedFlow.tryEmit(value)

  @Suppress("unused") val subscriptionCount: StateFlow<Int> = mutableSharedFlow.subscriptionCount

  suspend fun complete(throwable: Throwable? = null) {
    mutableSharedFlow.emit(Completion(throwable))
  }

  override suspend fun collectSafely(collector: FlowCollector<T>) {
    internalCompletingSharedFlow.collectSafely(collector)
  }
}

fun <T> CompletingSharedFlow<T>.onSubscription(
    action: suspend FlowCollector<T>.() -> Unit
): Flow<T> = InternalCompletingSharedFlow {
  when (this) {
        is InternalCompletingSharedFlow -> sharedFlowSupplier
        is MutableCompletingSharedFlow -> internalCompletingSharedFlow.sharedFlowSupplier
      }()
      .onSubscription(action)
}

/**
 * Similar to [shareIn], but completions and errors are also propagated to the downstream
 * subscribers.
 */
fun <T : Any?> Flow<T>.shareInCompleting(
    scope: CoroutineScope,
    started: SharingStarted,
    replay: Int = 0,
): CompletingSharedFlow<T> {
  val reference = AtomicReference<SharedFlow<Any?>>()

  return InternalCompletingSharedFlow {
    var sharedFlow: SharedFlow<Any?>? = null
    while (sharedFlow == null) {
      val current = reference.get()
      if (current != null) sharedFlow = current

      val computedSharedFlow: SharedFlow<Any?> =
          materializeUnboxed()
              .transformWhile {
                emit(it)
                it !is Completion
              }
              .onCompletion { reference.set(null) }
              .shareIn(scope, started, replay)

      if (reference.compareAndSet(null, computedSharedFlow)) sharedFlow = computedSharedFlow
    }
    sharedFlow
  }
}

internal class InternalCompletingSharedFlow<T>(
    internal val sharedFlowSupplier:
        suspend () -> SharedFlow<Any?> // Note that invoking this launches the shared flow
) : CompletingSharedFlow<T>() {

  override suspend fun collectSafely(collector: FlowCollector<T>) {
    sharedFlowSupplier().dematerializeUnboxed<T>().collect(collector)
  }
}
