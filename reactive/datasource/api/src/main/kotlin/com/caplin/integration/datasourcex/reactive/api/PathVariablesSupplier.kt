package com.caplin.integration.datasourcex.reactive.api

@Deprecated(
    "Use RequestSupplier, whose Request also carries the subject's query parameters.",
    ReplaceWith("RequestSupplier<T>"),
)
fun interface PathVariablesSupplier<T> {
  operator fun invoke(path: String, pathVariables: Map<String, String>): T
}
