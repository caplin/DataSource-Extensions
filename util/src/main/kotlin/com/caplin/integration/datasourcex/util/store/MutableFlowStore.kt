package com.caplin.integration.datasourcex.util.store

import com.caplin.integration.datasourcex.util.flow.VersionedMapEvent
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Read/write view of a store-backed map. Every mutation takes the caller's transaction handle [T]:
 * the write is enlisted on it through [CacheWriter], which assigns the version, and the cache
 * update and delta are published only when that transaction commits.
 *
 * The owner must **serialise writes to a given key** (single-writer-per-key): the version is the
 * store's commit order, so unserialised concurrent writes to one key would settle on the wrong
 * version. Serialise at the transaction layer — a locking read such as `SELECT … FOR UPDATE` via
 * [CacheLoaderWriter.load], held across the transaction — not an in-process lock, which orders the
 * calls but not the commits. [V] must be an aggregate root — see [FlowStore].
 */
interface MutableFlowStore<K : Any, V : Any, T> : FlowStore<K, V> {
  override val async: AsyncMutableFlowStore<K, V, T>

  /**
   * Reads [key]'s current value within [tx], always through the store and bypassing the cache, so
   * it sees this transaction's own uncommitted writes and can take a locking read. Use for
   * read-modify-write; use the cache-first [get] outside a transaction.
   */
  fun get(key: K, tx: T): V?

  fun put(key: K, value: V, tx: T)

  fun putAll(from: Map<K, V>, tx: T)

  fun remove(key: K, tx: T)
}

/**
 * Creates a [MutableFlowStore] backed by [store] and the bounded hot set [cache].
 *
 * Each mutation takes the caller's transaction handle [T], which [txContext] adapts into a
 * [TxContext] so the write can enlist on it and register its publish. Mutations are non-suspending
 * and run on that transaction; on commit the store refreshes its cache and `tryEmit`s the delta
 * onto its stream. The stream's buffer is unbounded so the commit callback never suspends on
 * backpressure; a permanently slow consumer therefore grows the buffer without bound (eventually
 * OOM) rather than blocking the committer. [store] assigns each write's version; the blocking [get]
 * read-through runs on the caller's thread, as the writes do. [V] must be an aggregate root — see
 * [FlowStore].
 */
fun <K : Any, V : Any, T> mutableFlowStore(
    store: CacheLoaderWriter<K, V, T>,
    cache: Cache<K, CacheEntry<V>>,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    txContext: (T) -> TxContext<T>,
): MutableFlowStore<K, V, T> =
    MutableFlowStoreImpl(store, FlowStoreCache(cache), dispatcher, txContext)

internal class MutableFlowStoreImpl<K : Any, V : Any, T>(
    private val writer: CacheLoaderWriter<K, V, T>,
    cache: FlowStoreCache<K, V>,
    dispatcher: CoroutineDispatcher,
    private val txContext: (T) -> TxContext<T>,
    // Unbounded buffer so the commit callback's tryEmit always succeeds without suspending: a slow
    // consumer grows the buffer, never blocks the committing thread.
) : AbstractFlowStore<K, V>(writer, cache, dispatcher, Int.MAX_VALUE), MutableFlowStore<K, V, T> {

  override val async: AsyncMutableFlowStore<K, V, T> by lazy {
    AsyncMutableFlowStoreImpl(this, dispatcher)
  }

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
