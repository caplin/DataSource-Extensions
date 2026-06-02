package com.caplin.integration.datasourcex.util.store

/** Combines [CacheLoader] and [CacheWriter] for a backend that implements both. */
interface CacheLoaderWriter<K : Any, V : Any, T> : CacheLoader<K, V>, CacheWriter<K, V, T>
