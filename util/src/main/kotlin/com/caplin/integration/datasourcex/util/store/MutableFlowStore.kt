package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.cache.SuspendingCache
import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent

/**
 * Creates a [MutableFlowStore] backed by [store] and the bounded hot set [cache].
 *
 * The store **participates in** transactions the caller owns rather than opening them: each
 * mutation takes the backend's transaction handle [T] (a jOOQ `Configuration`, a JDBC `Connection`,
 * …), which [txContext] adapts into a [TxContext] so the write can enlist and register its publish.
 * Mutations are non-suspending and run synchronously on the caller's (blocking) transaction; on
 * commit the store refreshes its cache and `tryEmit`s the delta onto its stream. The stream's
 * buffer is unbounded, so the commit callback never suspends or blocks on stream backpressure: a
 * slow consumer grows the buffer rather than parking the committing thread. [store] assigns each
 * write's version. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    store: CacheLoaderWriter<K, V, T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    txContext: (T) -> TxContext<T>,
): MutableFlowStore<K, V, T> = MutableFlowStoreImpl(store, store, cache, txContext)

internal class MutableFlowStoreImpl<K : Any, V : Any, T>(
    loader: CacheLoader<K, V>,
    private val writer: CacheLoaderWriter<K, V, T>,
    cache: SuspendingCache<K, CacheEntry<V>>,
    private val txContext: (T) -> TxContext<T>,
    // Unbounded buffer so the commit callback's tryEmit always succeeds without suspending: a slow
    // consumer grows the buffer, never blocks the committing thread.
) : AbstractFlowStore<K, V>(loader, cache, Int.MAX_VALUE), MutableFlowStore<K, V, T> {

  override fun get(key: K, tx: T): V? = writer.load(key, txContext(tx))?.value

  override fun put(key: K, value: V, tx: T) {
    val ctx = txContext(tx)
    val version = writer.write(key, value, ctx)
    ctx.onCommitEnd { publish(VersionedMapEvent.Upsert(key, value, version), Live(value, version)) }
  }

  override fun putAll(from: Map<K, V>, tx: T) {
    val ctx = txContext(tx)
    val versions = writer.writeAll(from, ctx)
    ctx.onCommitEnd {
      versions.forEach { (key, version) ->
        val value = from.getValue(key)
        publish(VersionedMapEvent.Upsert(key, value, version), Live(value, version))
      }
    }
  }

  override fun remove(key: K, tx: T) {
    val ctx = txContext(tx)
    val version = writer.delete(key, ctx)
    ctx.onCommitEnd { publish(VersionedMapEvent.Removed(key, version), Tombstone(version)) }
  }

  /**
   * Emits [event] before refreshing the cache with [entry]: the (unbounded) emit always accepts the
   * delta, so the owner cache can never sit ahead of an unpublished delta. The cache write is
   * version-gated so an interleaved post-commit reflection cannot regress it. Both steps are
   * non-suspending, so this runs in full inside the synchronous commit callback.
   */
  private fun publish(event: VersionedMapEvent<K, V>, entry: CacheEntry<V>) {
    signal.tryEmit(event)
    cachePutIfNewer(event.key, entry)
  }
}
