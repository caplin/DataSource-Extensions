package com.caplin.integration.datasourcex.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A subject, in its string form and decomposed into its path and query parameters.
 *
 * @property path The subject string.
 * @property pathParameters The subject's path parts.
 * @property queryParameters The subject's query parameters, or empty when there is no query.
 */
interface Subject {
  val path: String
  val pathParameters: List<String>
  val queryParameters: Map<String, String>

  companion object {
    /**
     * Creates a [Subject] from its [pathParameters] and optional [queryParameters]. Its [path] is
     * rendered by URL-encoding each path part and joining them under a leading `/`, with any query
     * parameters appended as `?a=b&c=d`.
     */
    @JvmStatic
    @JvmOverloads
    operator fun invoke(
        pathParameters: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): Subject {
      val parts = pathParameters
      val query = queryParameters
      return object : Subject {
        override val pathParameters = parts
        override val queryParameters = query
        override val path = buildPath(parts, query)

        override fun toString() = path
      }
    }

    /** Creates a [Subject] from its path parts, with no query parameters. */
    @JvmStatic
    operator fun invoke(vararg pathParameters: String): Subject = invoke(pathParameters.asList())

    private fun buildPath(
        pathParameters: List<String>,
        queryParameters: Map<String, String>,
    ): String {
      val path = pathParameters.joinToString(separator = "/", prefix = "/") { it.encode() }
      if (queryParameters.isEmpty()) return path
      return queryParameters.entries.joinToString(separator = "&", prefix = "$path?") {
          (name, value) ->
        "${name.encode()}=${value.encode()}"
      }
    }

    private fun String.encode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")
  }
}
