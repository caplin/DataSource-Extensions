package com.caplin.integration.datasourcex.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.util.AntPathMatcher

// To ensure the best compatibility these tests are adapted from
// https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/test/java/org/springframework/util/AntPathMatcherTests.java
// There are some fringe case differences mentioned in the tests below
class AntRegexPathMatcherTest :
    FunSpec({
      val antMatcher = AntPathMatcher()

      fun match(antPattern: String, path: String): Boolean {
        println()
        val antRegexPathMatcher = AntRegexPathMatcher(antPattern)
        val matched = antRegexPathMatcher.regex.matchEntire(path) != null
        println("$antPattern as ${antRegexPathMatcher.regex} for $path: $matched")
        val posixMatched =
            antRegexPathMatcher.posixExtendedPattern.toRegex().matchEntire(path) != null
        println(
            "$antPattern as ${antRegexPathMatcher.posixExtendedPattern} for $path: $posixMatched")

        val match = antMatcher.match(antPattern, path)
        match shouldBe matched
        match shouldBe posixMatched

        return matched && posixMatched
      }

      fun extractUriTemplateVariables(antPattern: String, path: String): Map<String, String> {
        val antRegexPathMatcher = AntRegexPathMatcher(antPattern)
        println("$antPattern as ${antRegexPathMatcher.regex} for $path")
        val matchResult = antRegexPathMatcher.regex.matchEntire(path)
        val regexPathVariables =
            antRegexPathMatcher.pathVariables.associateWith { matchResult!!.groups[it]!!.value }

        antMatcher.extractUriTemplateVariables(antPattern, path) shouldContainExactly
            regexPathVariables

        return regexPathVariables
      }

      test("match") {
        match("test", "test") shouldBe true
        match("/test", "/test") shouldBe true
        match("https://example.org", "https://example.org") shouldBe true
        match("/test.jpg", "test.jpg") shouldBe false
        match("test", "/test") shouldBe false
        match("/test", "test") shouldBe false

        // test matching with ?'s
        match("t?st", "test") shouldBe true
        match("??st", "test") shouldBe true
        match("tes?", "test") shouldBe true
        match("te??", "test") shouldBe true
        match("?es?", "test") shouldBe true
        match("tes?", "tes") shouldBe false
        match("tes?", "testt") shouldBe false
        match("tes?", "tsst") shouldBe false

        // test matching with *'s
        match("*", "test") shouldBe true
        match("test*", "test") shouldBe true
        match("test*", "testTest") shouldBe true
        match("test/*", "test/Test") shouldBe true
        match("test/*", "test/t") shouldBe true
        match("test/*", "test/") shouldBe true
        match("*test*", "AnothertestTest") shouldBe true
        match("*test", "Anothertest") shouldBe true
        match("*.*", "test.") shouldBe true
        match("*.*", "test.test") shouldBe true
        match("*.*", "test.test.test") shouldBe true
        match("test*aaa", "testblaaaa") shouldBe true
        match("test*", "tst") shouldBe false
        match("test*", "tsttest") shouldBe false
        match("test*", "test/") shouldBe false
        match("test*", "test/t") shouldBe false
        match("test/*", "test") shouldBe false
        match("*test*", "tsttst") shouldBe false
        match("*test", "tsttst") shouldBe false
        match("*.*", "tsttst") shouldBe false
        match("test*aaa", "test") shouldBe false
        match("test*aaa", "testblaaab") shouldBe false

        // test matching with ?'s and /'s
        match("/?", "/a") shouldBe true
        match("/?/a", "/a/a") shouldBe true
        match("/a/?", "/a/b") shouldBe true
        match("/??/a", "/aa/a") shouldBe true
        match("/a/??", "/a/bb") shouldBe true
        match("/?", "/a") shouldBe true

        // test matching with **'s
        match("/**", "/testing/testing") shouldBe true
        match("/*/**", "/testing/testing") shouldBe true
        match("/**/*", "/testing/testing") shouldBe true
        match("/bla/**/bla", "/bla/testing/testing/bla") shouldBe true
        match("/bla/**/bla", "/bla/testing/testing/bla/bla") shouldBe true
        match("/**/test", "/bla/bla/test") shouldBe true
        match("/bla/**/**/bla", "/bla/bla/bla/bla/bla/bla") shouldBe true
        match("/bla*bla/test", "/blaXXXbla/test") shouldBe true
        match("/*bla/test", "/XXXbla/test") shouldBe true
        match("/bla*bla/test", "/blaXXXbl/test") shouldBe false
        match("/*bla/test", "XXXblab/test") shouldBe false
        match("/*bla/test", "XXXbl/test") shouldBe false
        match("/????", "/bala/bla") shouldBe false
        match("/**/*bla", "/bla/bla/bla/bbb") shouldBe false
        match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing/") shouldBe true
        match("/*bla*/**/bla/*", "/XXXblaXXXX/testing/testing/bla/testing") shouldBe true
        match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing") shouldBe true

        match("/*bla*/**/bla/**", "/XXXblaXXXX/testing/testing/bla/testing/testing.jpg") shouldBe
            true
        match("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing/") shouldBe true
        match("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing") shouldBe true
        match("*bla*/**/bla/**", "XXXblaXXXX/testing/testing/bla/testing/testing") shouldBe true
        match("*bla*/**/bla/*", "XXXblaXXXX/testing/testing/bla/testing/testing") shouldBe false
        match("/x/x/**/bla", "/x/x/x/") shouldBe false
        match("/foo/bar/**", "/foo/bar") shouldBe true
        match("", "") shouldBe true
        match("/{bla}.*", "/testing.html") shouldBe true

        //        We do not support the below, unlike the ant path matcher.
        //
        //        match("/{bla}", "//x\ny") shouldBe true

        //        We cannot support path variable regex since we cannot translate it to POSIX regex
        // for
        // the C processes.
        runCatching { match("/{var:.*}", "/a") }
            .exceptionOrNull()
            .shouldBeInstanceOf<UnsupportedOperationException>()
            .message
            .shouldNotBeNull() shouldBeEqual "Regex path variable matching is not supported"
      }

      test("Extract uri template variables") {
        extractUriTemplateVariables("/hotels/{hotel}", "/hotels/1") shouldContainExactly
            mapOf("hotel" to "1")
        extractUriTemplateVariables("/h?tels/{hotel}", "/hotels/1") shouldContainExactly
            mapOf("hotel" to "1")
        extractUriTemplateVariables(
            "/hotels/{hotel}/bookings/{booking}",
            "/hotels/1/bookings/2",
        ) shouldContainExactly mapOf("hotel" to "1", "booking" to "2")
        extractUriTemplateVariables(
            "/**/hotels/**/{hotel}", "/foo/hotels/bar/1") shouldContainExactly mapOf("hotel" to "1")
        extractUriTemplateVariables("/{page}.html", "/42.html") shouldContainExactly
            mapOf("page" to "42")
        extractUriTemplateVariables("/{page}.*", "/42.html") shouldContainExactly
            mapOf("page" to "42")
        extractUriTemplateVariables("/A-{B}-C", "/A-b-C") shouldContainExactly mapOf("B" to "b")
        extractUriTemplateVariables("/{name}.{extension}", "/test.html") shouldContainExactly
            mapOf("name" to "test", "extension" to "html")
      }

      test("Consistent match with wildcards and trailing slash") {
        match("/*/foo", "/en/foo") shouldBe true
        match("/*/foo", "/en/foo/") shouldBe false
        match("/**/foo", "/en/foo") shouldBe true
        match("/**/foo", "/en/foo/") shouldBe false
      }
    })
