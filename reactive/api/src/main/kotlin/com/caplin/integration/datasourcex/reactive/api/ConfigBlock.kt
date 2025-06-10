package com.caplin.integration.datasourcex.reactive.api

fun interface ConfigBlock<T : Any> {
  operator fun T.invoke()
}
