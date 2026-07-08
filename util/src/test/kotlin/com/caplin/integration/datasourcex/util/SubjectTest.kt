package com.caplin.integration.datasourcex.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SubjectTest :
    FunSpec({
      test("vararg path parts render under a leading slash") {
        val subject = Subject("real", "abc")

        subject.pathParameters shouldBe listOf("real", "abc")
        subject.queryParameters shouldBe emptyMap()
        subject.path shouldBe "/real/abc"
        subject.toString() shouldBe "/real/abc"
      }

      test("empty path renders as a single slash") { Subject().path shouldBe "/" }

      test("path parts are URL-encoded, with spaces as %20") {
        Subject("a/b", "c d").path shouldBe "/a%2Fb/c%20d"
      }

      test("query parameters are appended and URL-encoded") {
        Subject(listOf("x"), mapOf("a b" to "c/d")).path shouldBe "/x?a%20b=c%2Fd"
      }

      test("multiple query parameters are joined with & in order") {
        Subject(listOf("x"), linkedMapOf("a" to "1", "b" to "2")).path shouldBe "/x?a=1&b=2"
      }

      test("no query parameters means no trailing ?") {
        Subject(listOf("x", "y")).path shouldBe "/x/y"
      }
    })
