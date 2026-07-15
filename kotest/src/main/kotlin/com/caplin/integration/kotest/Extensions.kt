package com.caplin.integration.kotest

import com.caplin.integration.streamlinkx.StreamLinkConnection
import io.kotest.core.spec.Spec
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

val StreamLinkConnection.awaitConnected: suspend (Spec) -> Unit
  get() = {
    withContext(Default) {
      withTimeoutOrNull(1000) { awaitConnected() }
          ?: error("Timed out waiting to connect for $username")
    }
  }
