package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.DataSource
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.json.JsonChannelMessage
import com.caplin.datasource.messaging.json.JsonHandler
import com.caplin.datasource.messaging.json.JsonMessage
import com.caplin.integration.datasourcex.reactive.api.JsonText
import com.caplin.integration.datasourcex.reactive.api.JsonTree
import java.util.concurrent.ConcurrentHashMap

internal class JsonContext(private val dataSource: DataSource) {

  private companion object {
    private val treeRoots = ConcurrentHashMap<JsonHandler<*>, Class<*>>()

    /** The base class of every tree [handler] makes, e.g. Jackson 3's for a Jackson 3 handler. */
    private fun treeRoot(handler: JsonHandler<Any?>): Class<*> =
        treeRoots.computeIfAbsent(handler) {
          generateSequence(checkNotNull(handler.parse("{}")).javaClass) { it.superclass }
              .last { it != Any::class.java }
        }
  }

  // Read on every use: the DataSource's handler can be replaced after binding.
  @Suppress("UNCHECKED_CAST")
  private val jsonHandler: JsonHandler<Any?>
    get() = dataSource.extraConfiguration.jsonHandler as JsonHandler<Any?>

  fun CachedMessageFactory.createMessage(path: String, value: Any): JsonMessage =
      createJsonMessage(path, toJsonValue(value))

  /**
   * [value] as the DataSource sends it: [JsonText] and foreign [JsonTree]s as the handler's tree.
   */
  fun toJsonValue(value: Any): Any =
      when (value) {
        is JsonText -> checkNotNull(jsonHandler.parse(value.json))
        is JsonTree ->
            jsonHandler.let { handler ->
              if (treeRoot(handler).isInstance(value.tree)) value.tree
              else checkNotNull(handler.parse(value.tree.toString()))
            }
        else -> value
      }

  fun <R : Any> JsonChannelMessage.decode(receiveType: Class<R>): R =
      when (receiveType) {
        JsonText::class.java -> receiveType.cast(JsonText(jsonHandler.format(jsonObject)))
        JsonTree::class.java -> receiveType.cast(JsonTree(checkNotNull(jsonObject)))
        else -> getJsonAsType(receiveType)
      }
}
