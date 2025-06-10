package com.caplin.integration.datasourcex.util

import java.util.regex.Pattern
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * Represents an Ant-style path parsed into a [Regex] and associated metadata.
 *
 * @property regex The compiled regular expression pattern.
 * @property posixExtendedPattern A POSIX-Extended compatible version of the regex.
 * @property pathVariables The list of path variable names extracted from the pattern.
 * @property exact Indicates whether the path pattern was exact, or contained wildcards or path
 *   variables.
 */
class AntRegexPathMatcher(
    val regex: Regex,
    val posixExtendedPattern: String,
    val pathVariables: Set<String>,
    val exact: Boolean,
) {
  companion object {
    private const val NON_SLASH_CHARACTER = "[^/]"
    private const val POSIX_TRAILING_SLASH = """\/"""
    private const val NON_POSIX_TRAILING_SLASH = """/\E"""

    /**
     * Parses an Ant-style path pattern into regular expressions and associated metadata.
     * > This does not support regex nested within the provided pattern.
     *
     * @param antPattern The Ant-style path pattern to compile.
     * @return An AntRegexPathMatcher object that can be used to match paths against the compiled
     *   regular expression pattern.
     */
    @JvmStatic
    operator fun invoke(antPattern: String): AntRegexPathMatcher {
      val groupRegex = patternToRegex(antPattern, false)
      val posixRegex = patternToRegex(antPattern, true)
      return AntRegexPathMatcher(
          groupRegex.regex.toRegex(DOT_MATCHES_ALL),
          posixRegex.regex,
          groupRegex.pathVariables,
          groupRegex.exact,
      )
    }

    /**
     * @param antPattern The Ant-style path pattern to convert.
     * @param posix Indicates whether the output regular expression pattern should be POSIX safe (no
     *   group matchers).
     */
    private fun patternToRegex(antPattern: String, posix: Boolean) =
        object {
          val regex: String
          val pathVariables: Set<String>
          val exact: Boolean

          val trailingSlash = if (posix) POSIX_TRAILING_SLASH else NON_POSIX_TRAILING_SLASH

          init {
            val trimmedLine = antPattern.trim()
            val trimmedLength = trimmedLine.length
            val sb = StringBuilder()

            var i = 0
            sb.append("^")

            val pathVariables = mutableSetOf<String>()
            var exact = true

            while (i < trimmedLength) {
              val char = trimmedLine[i++]
              sb.append(
                  when (char) {
                    '?' -> {
                      exact = false
                      "."
                    }

                    '*' -> {
                      exact = false
                      when (trimmedLine.getOrNull(i)) {
                        '*' -> {
                          val hasTrailingSlash = sb.endsWith(trailingSlash)

                          if (hasTrailingSlash) sb.setLength(sb.length - trailingSlash.length)
                          i++

                          if (hasTrailingSlash && !posix) """\E.*""" else ".*"
                        }

                        else -> "$NON_SLASH_CHARACTER*"
                      }
                    }

                    '{' -> {
                      exact = false
                      val range = i until trimmedLine.indexOf('}', i)
                      val pathVariable = trimmedLine.slice(range)

                      pathVariables.add(pathVariable)

                      i = range.last + 2
                      when (pathVariable.indexOf(":")) {
                        -1 ->
                            if (posix) "$NON_SLASH_CHARACTER*"
                            else "(?<${pathVariable}>$NON_SLASH_CHARACTER*)"

                        else ->
                            throw UnsupportedOperationException(
                                "Regex path variable matching is not supported",
                            )
                      }
                    }

                    else -> {
                      val quoted =
                          trimmedLine.drop(i - 1).takeWhile {
                            when (it) {
                              '?',
                              '*',
                              '{' -> false

                              else -> true
                            }
                          }
                      i += quoted.length - 1

                      (if (posix) quoted.replace(Regex("[^a-zA-Z0-9]")) { """\${it.value}""" }
                      else Pattern.quote(quoted))
                    }
                  },
              )
            }
            sb.append("$")

            regex = sb.toString()
            this.pathVariables = pathVariables
            this.exact = exact
          }
        }
  }

  override fun toString(): String {
    return "AntRegexPathMatcher(regex=$regex, posixExtendedPattern='$posixExtendedPattern', " +
        "pathVariables=$pathVariables, exact=$exact)"
  }
}
