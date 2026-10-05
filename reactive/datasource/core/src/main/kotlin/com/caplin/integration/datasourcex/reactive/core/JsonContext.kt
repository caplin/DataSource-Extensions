package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.DataSource
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.json.JsonChannelMessage
import com.caplin.datasource.messaging.json.JsonHandler
import com.caplin.datasource.messaging.json.JsonMessage
import com.caplin.integration.datasourcex.reactive.api.JsonText

internal class JsonContext(private val dataSource: DataSource) {

  // Read on every use: the DataSource's handler can be replaced after binding.
  @Suppress("UNCHECKED_CAST")
  private val jsonHandler: JsonHandler<Any?>
    get() = dataSource.extraConfiguration.jsonHandler as JsonHandler<Any?>

  fun CachedMessageFactory.createMessage(path: String, value: Any): JsonMessage =
      createJsonMessage(path, toJsonValue(value))

  /** [value] as the DataSource sends it: [JsonText] parsed to the handler's tree. */
  fun toJsonValue(value: Any): Any =
      if (value is JsonText) checkNotNull(jsonHandler.parse(value.json)) else value

  fun <R : Any> JsonChannelMessage.decode(receiveType: Class<R>): R =
      if (receiveType == JsonText::class.java)
          receiveType.cast(JsonText(jsonHandler.format(jsonObject)))
      else getJsonAsType(receiveType)
}
