package com.caplin.integration.datasourcex.reactive.api

fun interface PathVariablesSupplier<T> {
  operator fun invoke(path: String, pathVariables: Map<String, String>): T
}
