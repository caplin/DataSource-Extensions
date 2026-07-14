package com.caplin.integration.datasourcex.util

import com.caplin.integration.datasourcex.util.Subject.Companion.path
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.SortedMap

/**
 * A subject, decomposed into its path and query parameters. Its string form is rendered on demand
 * by the [path] extension property.
 *
 * @property pathParameters The subject's path parts.
 * @property queryParameters The subject's query parameters, sorted by name so that equivalent
 *   subjects render identically, or empty when there is no query.
 */
interface Subject {
  val pathParameters: List<String>
  val queryParameters: SortedMap<String, String>

  companion object {

    private data class SubjectImpl(
        override val pathParameters: List<String>,
        override val queryParameters: SortedMap<String, String>,
    ) : Subject

    /**
     * Creates a [Subject] from its [pathParameters] and optional [queryParameters]. Its [path] is
     * rendered by URL-encoding each path part and joining them under a leading `/`, with any query
     * parameters appended as `?a=b&c=d` in name order.
     */
    @JvmStatic
    @JvmOverloads
    operator fun invoke(
        pathParameters: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): Subject = SubjectImpl(pathParameters, queryParameters.toSortedMap())

    /** Creates a [Subject] from its path parts, with no query parameters. */
    @JvmStatic
    operator fun invoke(vararg pathParameters: String): Subject = invoke(pathParameters.asList())

    /** The subject's string form, rendered from its path parts and query parameters. */
    @JvmStatic
    val Subject.path: String
      get() = buildPath(pathParameters, queryParameters)

    fun buildPath(
        pathParameters: List<String>,
        queryParameters: SortedMap<String, String>,
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
