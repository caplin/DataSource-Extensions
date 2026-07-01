package com.caplin.integration.datasourcex.reactive.api

fun interface RequestSupplier<T> {
  operator fun Request.invoke(): T
}
