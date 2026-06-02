package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent

/**
 * Creates a [MutableFlowStore] backed by [store] and the bounded hot set [cache].
 *
 * Each mutation takes the caller's transaction handle [T] (a jOOQ `Configuration`, a JDBC
 * `Connection`, …), which [txContext] adapts into a [TxContext] so the write can enlist on it and
 * register its publish. Mutations are non-suspending and run on that transaction; on commit the
 * store refreshes its cache and `tryEmit`s the delta onto its stream. The stream's buffer is
 * unbounded so the commit callback never suspends on backpressure; a permanently slow consumer
 * therefore grows the buffer without bound (eventually OOM) rather than blocking the committer.
 * [store] assigns each write's version. [V] must be an aggregate root — see [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    store: CacheLoaderWriter<K, V, T>,
    cache: FlowStoreCache<K, V>,
    txContext: (T) -> TxContext<T>,
): MutableFlowStore<K, V, T> = MutableFlowStoreImpl(store, cache, txContext)

internal class MutableFlowStoreImpl<K : Any, V : Any, T>(
    private val writer: CacheLoaderWriter<K, V, T>,
    cache: FlowStoreCache<K, V>,
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
    // Build the deltas eagerly, before commit, so a writer that omits a key fails the transaction
    // here rather than throwing from the post-commit callback after the writes are durable.
    val deltas =
        from.map { (key, value) ->
          val version = versions[key] ?: error("writeAll returned no version for key $key")
          VersionedMapEvent.Upsert(key, value, version)
        }
    ctx.onCommitEnd { deltas.forEach(::publish) }
  }

  override fun remove(key: K, tx: T) {
    val ctx = txContext(tx)
    val version = writer.delete(key, ctx)
    ctx.onCommitEnd { publish(VersionedMapEvent.Removed(key, version)) }
  }

  /**
   * Emits [event] then updates the cache: the unbounded emit always accepts the delta, so the cache
   * never sits ahead of an unpublished delta. Both steps are non-suspending and run inside the
   * synchronous commit callback.
   */
  private fun publish(event: VersionedMapEvent<K, V>) {
    signal.tryEmit(event)
    cache.putIfNewer(event.key, event.toEntry())
  }
}
