package com.caplin.integration.datasourcex.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual

class AntPatternNamespaceTest :
    FunSpec({
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
