package com.caplin.integration.datasourcex.util.store

/** Something stamped with the monotonically increasing [version] it was written at. */
interface Versionable {
  val version: Long
}
