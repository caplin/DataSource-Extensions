package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent

/**
 * Creates a [MutableFlowStore] backed by [store] and the bounded hot set [cache].
 *
 * Each mutation takes the caller's transaction handle [T] (a jOOQ `Configuration`, a JDBC
 * `Connection`, …), which [txContext] adapts into a [TxContext] so the write can enlist on it and
 * register its publish. Mutations are non-suspending and run on that transaction; on commit the
 * store refreshes its cache and `tryEmit`s the delta onto its stream. The stream's buffer is
 * unbounded, so the commit callback never suspends on stream backpressure — a slow consumer grows
 * the buffer instead. [store] assigns each write's version. [V] must be an aggregate root — see
 * [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    store: CacheLoaderWriter<K, V, T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    txContext: (T) -> TxContext<T>,
): MutableFlowStore<K, V, T> = MutableFlowStoreImpl(store, cache, txContext)

internal class MutableFlowStoreImpl<K : Any, V : Any, T>(
    private val writer: CacheLoaderWriter<K, V, T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    private val txContext: (T) -> TxContext<T>,
    // Unbounded buffer so the commit callback's tryEmit always succeeds without suspending: a slow
    // consumer grows the buffer, never blocks the committing thread.
) : AbstractFlowStore<K, V>(writer, cache, Int.MAX_VALUE), MutableFlowStore<K, V, T> {

  override fun get(key: K, tx: T): V? = writer.load(key, txContext(tx))?.value

  override fun put(key: K, value: V, tx: T) {
    val ctx = txContext(tx)
    val version = writer.write(key, value, ctx)
    ctx.onCommitEnd { publish(VersionedMapEvent.Upsert(key, value, version)) }
  }

  override fun putAll(from: Map<K, V>, tx: T) {
    val ctx = txContext(tx)
    val versions = writer.writeAll(from, ctx)
    ctx.onCommitEnd {
      from.forEach { (key, value) ->
        publish(VersionedMapEvent.Upsert(key, value, versions.getValue(key)))
      }
    }
  }

  override fun remove(key: K, tx: T) {
    val ctx = txContext(tx)
    val version = writer.delete(key, ctx)
    ctx.onCommitEnd { publish(VersionedMapEvent.Removed(key, version)) }
  }

  /**
   * Emits [event] before refreshing the cache: the (unbounded) emit always accepts the delta, so
   * the owner cache can never sit ahead of an unpublished delta. The cache write is version-gated
   * so an interleaved post-commit reflection cannot regress it. Both steps are non-suspending, so
   * this runs in full inside the synchronous commit callback.
   */
  private fun publish(event: VersionedMapEvent<K, V>) {
    signal.tryEmit(event)
    cachePutIfNewer(event.key, event.toEntry())
  }
}
