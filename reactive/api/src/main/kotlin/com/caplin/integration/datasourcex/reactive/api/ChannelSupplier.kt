package com.caplin.integration.datasourcex.reactive.api

fun interface ChannelSupplier<R, T> {
  operator fun invoke(receive: R): T
}
