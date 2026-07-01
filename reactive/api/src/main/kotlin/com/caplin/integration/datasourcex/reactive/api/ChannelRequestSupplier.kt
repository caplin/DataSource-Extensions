package com.caplin.integration.datasourcex.reactive.api

fun interface ChannelRequestSupplier<R, T> {
  operator fun ChannelRequest<R>.invoke(): T
}
