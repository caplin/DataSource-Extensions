package com.caplin.integration.datasourcex.util

import com.caplin.datasource.Service
import com.caplin.datasource.namespace.Namespace

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

  override fun match(subject: String): Boolean = matcher.regex.matchEntire(subject) != null

  /**
   * Extracts path variables from a matching subject.
   *
   * @param subject The subject to extract variables from. Must match the [pattern].
   * @return A map of path variable names to their extracted values.
   * @throws IllegalStateException If the subject does not match the pattern.
   */
  fun extractPathVariables(subject: String): Map<String, String> {
    val groups =
        checkNotNull(matcher.regex.matchEntire(subject)) {
              "Subject $subject does not match pattern $pattern"
            }
            .groups

    return matcher.pathVariables
        .mapNotNull { name -> groups[name]?.value?.let { value -> name to value } }
        .toMap()
  }

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
