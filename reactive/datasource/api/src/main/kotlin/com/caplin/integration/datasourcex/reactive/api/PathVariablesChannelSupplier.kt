package com.caplin.integration.datasourcex.reactive.api

@Deprecated(
    "Use ChannelRequestSupplier, whose ChannelRequest also carries the subject's query parameters.",
    ReplaceWith("ChannelRequestSupplier<R, T>"),
)
fun interface PathVariablesChannelSupplier<R, T> {
  operator fun invoke(path: String, pathVariables: Map<String, String>, receive: R): T
}
