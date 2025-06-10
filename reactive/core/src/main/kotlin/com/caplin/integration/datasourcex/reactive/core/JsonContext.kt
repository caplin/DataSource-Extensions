package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.json.JsonMessage

internal class JsonContext {

  fun CachedMessageFactory.createMessage(subject: String, value: Any): JsonMessage =
      createJsonMessage(subject, value)
}
