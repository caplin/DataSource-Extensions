package com.caplin.integration.datasourcex.util.cache

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

/**
 * A coroutine wrapper over Caffeine's [AsyncCache] with single-flight loading. Loads run in a child
 * [SupervisorJob] of [scope] rather than the caller's coroutine, so one caller cancelling its [get]
 * does not fail the shared computation, and a failed load surfaces only to that caller without
 * cancelling [scope]. [scope]'s dispatcher decides where loads run, so it is required (no default):
 * a blocking loader needs [kotlinx.coroutines.Dispatchers.IO], not the default dispatcher.
 */
open class SuspendingCache<K : Any, V : Any>(
    private val cache: AsyncCache<K, V>,
    scope: CoroutineScope,
) {
  private val loaderScope =
      CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

  fun asyncCache(): AsyncCache<K, V> = cache

  suspend fun getIfPresent(key: K): V? = cache.getIfPresent(key)?.await()

  /** Returns the cached value, loading it (single-flight) via [loader] on a miss. */
  suspend fun get(key: K, loader: suspend (K) -> V): V =
      cache.get(key) { k, _ -> loaderScope.future { loader(k) } }.await()

  /** Inserts a value directly, bypassing the loader. */
  fun put(key: K, value: V) {
    cache.put(key, CompletableFuture.completedFuture(value))
  }

  fun invalidate(key: K) {
    cache.synchronous().invalidate(key)
  }
}

/** A [SuspendingCache] with a fixed [loader] applied on every miss. */
class LoadingSuspendingCache<K : Any, V : Any>(
    cache: AsyncCache<K, V>,
    scope: CoroutineScope,
    private val loader: suspend (K) -> V,
) : SuspendingCache<K, V>(cache, scope) {
  suspend fun get(key: K): V = get(key, loader)
}

/** Builds a [SuspendingCache] from a configured Caffeine builder. */
fun <K : Any, V : Any> Caffeine<in K, in V>.buildSuspending(
    scope: CoroutineScope
): SuspendingCache<K, V> = SuspendingCache(buildAsync(), scope)

/** Builds a [LoadingSuspendingCache] with a fixed suspend [loader]. */
fun <K : Any, V : Any> Caffeine<in K, in V>.buildSuspending(
    scope: CoroutineScope,
    loader: suspend (K) -> V,
): LoadingSuspendingCache<K, V> = LoadingSuspendingCache(buildAsync(), scope, loader)
