package com.caplin.integration.datasourcex.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe

class AntPatternNamespaceTest :
    FunSpec({
      test("subject with trailing query matches iff its path portion matches") {
        val namespace = AntPatternNamespace("/CALENDAR/TENORDATES/{productPair}")

        namespace.match("/CALENDAR/TENORDATES/EURUSD?locationType=ONSHORE") shouldBe true
        namespace.match("/CALENDAR/TENORDATES/EURUSD") shouldBe true
        namespace.match("/CALENDAR/OTHER/EURUSD?locationType=ONSHORE") shouldBe false
      }

      test("path variables are not polluted by a trailing query") {
        val namespace = AntPatternNamespace("/CALENDAR/TENORDATES/{productPair}")

        namespace.extractPathVariables("/CALENDAR/TENORDATES/EURUSD?locationType=ONSHORE") shouldBe
            mapOf("productPair" to "EURUSD")
        namespace.extractPathVariables("/CALENDAR/TENORDATES/EURUSD") shouldBe
            mapOf("productPair" to "EURUSD")
      }

      test("extractPathVariables URL-decodes values") {
        AntPatternNamespace("/CALENDAR/TENORDATES/{productPair}")
            .extractPathVariables("/CALENDAR/TENORDATES/EUR%2FUSD?locationType=ONSHORE") shouldBe
            mapOf("productPair" to "EUR/USD")
      }

      test("extractPathVariables returns rawPathVariables verbatim without decoding") {
        val namespace =
            AntPatternNamespace(
                "/PRIVATE/{username}/{productPair}",
                rawPathVariables = setOf("username"),
            )

        namespace.extractPathVariables("/PRIVATE/john%2Fdoe/EUR%2FUSD") shouldBe
            mapOf("username" to "john%2Fdoe", "productPair" to "EUR/USD")
      }

      test("copy derives a namespace with different rawPathVariables but the same pattern") {
        val namespace = AntPatternNamespace("/PRIVATE/{username}/{productPair}")
        val raw = namespace.copy(rawPathVariables = setOf("username"))

        raw.pattern shouldBe namespace.pattern
        namespace.extractPathVariables("/PRIVATE/john%2Fdoe/EURUSD") shouldBe
            mapOf("username" to "john/doe", "productPair" to "EURUSD")
        raw.extractPathVariables("/PRIVATE/john%2Fdoe/EURUSD") shouldBe
            mapOf("username" to "john%2Fdoe", "productPair" to "EURUSD")
      }

      test("extractQueryParameters returns an empty map when there is no query") {
        AntPatternNamespace("/CALENDAR/TENORDATES/{productPair}")
            .extractQueryParameters("/CALENDAR/TENORDATES/EURUSD") shouldBe emptyMap()
      }

      test("extractQueryParameters parses a single parameter") {
        AntPatternNamespace("/CALENDAR/TENORDATES/{productPair}")
            .extractQueryParameters("/CALENDAR/TENORDATES/EURUSD?locationType=ONSHORE") shouldBe
            mapOf("locationType" to "ONSHORE")
      }

      test("extractQueryParameters parses multiple parameters") {
        AntPatternNamespace("/CALENDAR/TENORDATES/{productPair}")
            .extractQueryParameters(
                "/CALENDAR/TENORDATES/EURUSD?locationType=ONSHORE&tenor=SPOT"
            ) shouldBe mapOf("locationType" to "ONSHORE", "tenor" to "SPOT")
      }

      test("extractQueryParameters parses a query on an exact pattern with no path variables") {
        AntPatternNamespace("/CALENDAR/HOLIDAYS")
            .extractQueryParameters("/CALENDAR/HOLIDAYS?year=2026") shouldBe mapOf("year" to "2026")
      }

      test("extractQueryParameters URL-decodes keys and values") {
        AntPatternNamespace("/CALENDAR/{id}")
            .extractQueryParameters("/CALENDAR/x?a%20b=c%2Bd&e=%3D") shouldBe
            mapOf("a b" to "c+d", "e" to "=")
      }

      test("extractQueryParameters yields empty values for keys without a value") {
        AntPatternNamespace("/CALENDAR/{id}")
            .extractQueryParameters("/CALENDAR/x?flag&empty=") shouldBe
            mapOf("flag" to "", "empty" to "")
      }

      test("extractQueryParameters keeps the last value for a repeated key") {
        AntPatternNamespace("/CALENDAR/{id}").extractQueryParameters("/CALENDAR/x?a=1&a=2") shouldBe
            mapOf("a" to "2")
      }

      test("object maps are created as expected") {
        AntPatternNamespace("/PRIVATE/{mapping1}/FX/{baseCurrency}/{termCurrency}/*/{mapping2}/**")
            .getObjectMap(mapOf("mapping1" to "%u", "mapping2" to "%g"))
            .run {
              fromPattern shouldBeEqual "/PRIVATE/FX/%1/%2/%3/%4"
              toPattern shouldBeEqual "/PRIVATE/%u/FX/%1/%2/%3/%g/%4"
            }

        AntPatternNamespace("/PRIVATE/{mapping1}/{mapping2}/FX/{baseCurrency}/{termCurrency}/*/**")
            .getObjectMap(mapOf("mapping1" to "%u", "mapping2" to "%g"))
            .run {
              fromPattern shouldBeEqual "/PRIVATE/FX/%1/%2/%3/%4"
              toPattern shouldBeEqual "/PRIVATE/%u/%g/FX/%1/%2/%3/%4"
            }

        AntPatternNamespace("/post/{userId}/{sessionId}")
            .getObjectMap(mapOf("userId" to "%u", "sessionId" to "%s"))
            .run {
              fromPattern shouldBeEqual "/post"
              toPattern shouldBeEqual "/post/%u/%s"
            }

        AntPatternNamespace("/post/{userId}").getObjectMap(mapOf("userId" to "%u")).run {
          fromPattern shouldBeEqual "/post"
          toPattern shouldBeEqual "/post/%u"
        }
      }
    })
