package com.caplin.integration.datasourcex.util.store

/** Read half of the store SPI. */
interface StoreReader<K : Any, V : Any> {
  fun load(key: K): Versioned<V>?
}
