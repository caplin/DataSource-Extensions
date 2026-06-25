package com.caplin.integration.datasourcex.util.flow

import com.caplin.integration.datasourcex.util.store.Versionable

/**
 * A map mutation carrying the [version] it was written at, for distribution to consumers that gate
 * on `version >`. Unlike [MapEvent] there is no old value and no `Populated` marker: the stream is
 * delta-only and current values are read from the store.
 */
sealed interface VersionedMapEvent<out K : Any, out V : Any> : Versionable {
  val key: K
  override val version: Long

  data class Upsert<out K : Any, out V : Any>(
      override val key: K,
      val value: V,
      override val version: Long,
  ) : VersionedMapEvent<K, V>

  data class Removed<out K : Any>(override val key: K, override val version: Long) :
      VersionedMapEvent<K, Nothing>
}
