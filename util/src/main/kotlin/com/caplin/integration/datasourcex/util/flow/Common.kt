package com.caplin.integration.datasourcex.util.flow

internal val UNSET =
    object {
      @Suppress("UNCHECKED_CAST") operator fun <T : Any> invoke() = this as T
    }
