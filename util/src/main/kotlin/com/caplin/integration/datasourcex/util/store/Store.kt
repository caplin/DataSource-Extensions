package com.caplin.integration.datasourcex.util.store

/** Combines [StoreReader] and [StoreWriter] for a backend that implements both. */
interface Store<K : Any, V : Any, T> : StoreReader<K, V>, StoreWriter<K, V, T>
