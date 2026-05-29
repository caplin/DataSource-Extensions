package com.caplin.integration.datasourcex.util.serialization.jackson3

import com.caplin.datasource.messaging.json.JsonHandler
import com.flipkart.zjsonpatch.Jackson3JsonDiff
import com.flipkart.zjsonpatch.Jackson3JsonPatch
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * A Jackson 3 [JsonHandler] backed by an [ObjectMapper], using zjsonpatch for RFC 6902 diff/patch.
 */
public class Jackson3JsonHandler(private val objectMapper: ObjectMapper) : JsonHandler<JsonNode> {

  override fun toJsonTree(pojo: Any?): JsonNode = objectMapper.valueToTree(pojo)

  override fun toObject(jsonTree: JsonNode, userType: Class<*>): Any? =
      objectMapper.treeToValue(jsonTree, userType)

  override fun parse(jsonText: String): JsonNode = objectMapper.readTree(jsonText)

  override fun format(jsonTree: JsonNode): String = objectMapper.writeValueAsString(jsonTree)

  override fun diff(source: JsonNode, target: JsonNode): JsonNode =
      Jackson3JsonDiff.asJson(source, target)

  override fun patch(source: JsonNode, jsonPatch: JsonNode): JsonNode =
      Jackson3JsonPatch.apply(jsonPatch, source)
}
