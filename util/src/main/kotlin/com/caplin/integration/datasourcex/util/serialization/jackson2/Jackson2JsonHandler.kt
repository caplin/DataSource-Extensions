package com.caplin.integration.datasourcex.util.serialization.jackson2

import com.caplin.datasource.messaging.json.JsonHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.flipkart.zjsonpatch.JsonDiff
import com.flipkart.zjsonpatch.JsonPatch

/**
 * A Jackson 2 [JsonHandler] backed by an [ObjectMapper], using zjsonpatch for RFC 6902 diff/patch.
 */
public class Jackson2JsonHandler(private val objectMapper: ObjectMapper) : JsonHandler<JsonNode> {

  override fun toJsonTree(pojo: Any?): JsonNode = objectMapper.valueToTree(pojo)

  override fun toObject(jsonTree: JsonNode, userType: Class<*>): Any? =
      objectMapper.treeToValue(jsonTree, userType)

  override fun parse(jsonText: String): JsonNode = objectMapper.readTree(jsonText)

  override fun format(jsonTree: JsonNode): String = objectMapper.writeValueAsString(jsonTree)

  override fun diff(source: JsonNode, target: JsonNode): JsonNode = JsonDiff.asJson(source, target)

  override fun patch(source: JsonNode, jsonPatch: JsonNode): JsonNode =
      JsonPatch.apply(jsonPatch, source)
}
