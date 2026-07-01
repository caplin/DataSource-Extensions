package com.caplin.integration.datasourcex.util

import com.caplin.datasource.Service
import com.caplin.datasource.namespace.Namespace
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * An implementation of [Namespace] that matches subjects based on Ant style path patterns.
 *
 * This supports the majority of the rules laid out in Spring's
 * [AntPathMatcher](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html),
 * but with the exception that regex path variables are not supported.
 *
 * Simple path variables such as `{client}` are supported, and can be accessed via
 * [extractPathVariables].
 */
@Suppress("MaxLineLength")
class AntPatternNamespace(pattern: String) : Namespace {

  companion object {

    private const val MAX_WILDCARDS = 10

    /** The [Charset] used to URL-decode path variables and query parameters. */
    @JvmField val CHARSET: Charset = StandardCharsets.UTF_8

    /**
     * Parses the query portion of a subject (the part after the first `?`, without the `?` itself)
     * into a map of decoded parameter names to values.
     *
     * Names and values are URL-decoded. A parameter with no `=` (e.g. `flag`) or an empty value
     * (e.g. `empty=`) maps to an empty string. When a key repeats, the last value wins.
     */
    private fun parseQueryString(query: String): Map<String, String> =
        query.split("&").filter(String::isNotEmpty).associate { parameter ->
          urlDecode(parameter.substringBefore('=')) to urlDecode(parameter.substringAfter('=', ""))
        }

    private fun urlDecode(value: String): String = URLDecoder.decode(value, CHARSET)

    @JvmStatic
    fun Service.addIncludeNamespace(antPatternNamespace: AntPatternNamespace) {
      addIncludePattern(antPatternNamespace.posixExtendedPattern)
    }

    @JvmStatic
    fun Service.addExcludeNamespace(antPatternNamespace: AntPatternNamespace) {
      addExcludePattern(antPatternNamespace.posixExtendedPattern)
    }
  }

  /**
   * Represents a mapping between a from-pattern and a to-pattern, used for injecting user-specific
   * information into subjects requested by Liberator.
   *
   * @property fromPattern The pattern used to match the incoming subject.
   * @property toPattern The pattern used to map to the destination subject.
   */
  class ObjectMap(val fromPattern: String, val toPattern: String) {
    operator fun component1(): String = fromPattern

    operator fun component2(): String = toPattern

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ObjectMap

      if (fromPattern != other.fromPattern) return false
      if (toPattern != other.toPattern) return false

      return true
    }

    override fun hashCode(): Int {
      var result = fromPattern.hashCode()
      result = 31 * result + toPattern.hashCode()
      return result
    }

    override fun toString(): String {
      return "ObjectMap(fromPattern='$fromPattern', toPattern='$toPattern')"
    }
  }

  /** The Ant-style path pattern used by this namespace. */
  val pattern = pattern.removeSuffix("/")

  private val matcher = AntRegexPathMatcher(pattern)

  override fun match(subject: String): Boolean =
      matcher.regex.matchEntire(subject.pathPortion) != null

  /**
   * Extracts path variables from a matching subject. Values are URL-decoded. Any trailing `?query`
   * is ignored, so a subject that carries query parameters yields the same path variables as the
   * query-less subject.
   *
   * @param subject The subject to extract variables from. Its path portion must match the
   *   [pattern].
   * @return A map of path variable names to their extracted values.
   * @throws IllegalStateException If the subject does not match the pattern.
   */
  fun extractPathVariables(subject: String): Map<String, String> {
    val groups =
        checkNotNull(matcher.regex.matchEntire(subject.pathPortion)) {
              "Subject $subject does not match pattern $pattern"
            }
            .groups

    return matcher.pathVariables
        .mapNotNull { name -> groups[name]?.value?.let { value -> name to urlDecode(value) } }
        .toMap()
  }

  /**
   * Extracts the query parameters from the optional trailing `?a=b&c=d` portion of a subject.
   *
   * @param subject The subject to extract query parameters from.
   * @return A map of query parameter names to values, or an empty map when the subject has no
   *   query.
   */
  fun extractQueryParameters(subject: String): Map<String, String> =
      parseQueryString(subject.substringAfter('?', ""))

  /** The subject with any trailing `?query` removed. */
  private val String.pathPortion: String
    get() = substringBefore('?')

  /**
   * Indicates whether this pattern only matches one exact path (i.e. no use of `?`, `*`, `**` or
   * path variables).
   *
   * @return `true` if only one pattern will ever be matched.
   */
  val isExact by matcher::exact

  /**
   * Provides a POSIX Extended Regular Expression version of this pattern, suitable for use in
   * [Service.addIncludePattern] and [Service.addExcludePattern]
   */
  val posixExtendedPattern by matcher::posixExtendedPattern

  /** Provides a list of possible path variables. */
  val pathVariables by matcher::pathVariables

  /**
   * Creates from and to object-map patterns that can be used with [Service.addObjectMap] to inject
   * user-specific information into subjects requested by Liberator.
   */
  fun getObjectMap(mappings: Map<String, String>): ObjectMap {
    val mappingValues = mappings.keys
    val valuesRegex = mappingValues.joinToString("|") { """\Q{${it}}\E""" }
    val pathVariablesRegex = (pathVariables - mappingValues).map { """\Q{${it}}\E""" }

    var toPattern = pattern
    var wildcardCount = 1
    val replaceRegex = "(${(pathVariablesRegex + "[*?]").joinToString("|")})+".toRegex()
    while (true) {
      check(wildcardCount < MAX_WILDCARDS) {
        "Only ${MAX_WILDCARDS - 1} wildcard groups are supported, cannot create mapping for $pattern"
      }
      val oldMappedPattern = toPattern
      toPattern = toPattern.replaceFirst(replaceRegex, "%${wildcardCount++}")
      if (oldMappedPattern == toPattern) break
    }

    val fromPattern =
        toPattern
            .replace("($valuesRegex)((/|$valuesRegex)+?)?(|$valuesRegex)+".toRegex(), "")
            .removeSuffix("/")

    mappings.forEach { toPattern = toPattern.replace("{${it.key}}", it.value) }

    return ObjectMap(fromPattern, toPattern)
  }

  override fun toString(): String = "AntPathPatternNamespace(pattern='$pattern', matcher=$matcher)"
}
