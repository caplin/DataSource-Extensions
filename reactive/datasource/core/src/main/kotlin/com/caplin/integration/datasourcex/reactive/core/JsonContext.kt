package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.json.JsonMessage

internal class JsonContext {

  fun CachedMessageFactory.createMessage(path: String, value: Any): JsonMessage =
      createJsonMessage(path, value)
}
