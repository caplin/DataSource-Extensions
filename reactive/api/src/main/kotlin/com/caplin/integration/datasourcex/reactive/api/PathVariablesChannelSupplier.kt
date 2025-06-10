package com.caplin.integration.datasourcex.reactive.api

fun interface PathVariablesChannelSupplier<R, T> {
  operator fun invoke(path: String, pathVariables: Map<String, String>, receive: R): T
}
