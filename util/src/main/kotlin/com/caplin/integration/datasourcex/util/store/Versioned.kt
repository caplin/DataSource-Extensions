package com.caplin.integration.datasourcex.util.store

/** A [value] paired with the monotonically increasing [version] it was written at. */
data class Versioned<out V : Any>(val value: V, val version: Long)
