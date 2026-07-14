package com.caplin.integration.datasourcex.reactive.api

fun interface PathSupplier<T> {
  operator fun invoke(path: String): T
}
