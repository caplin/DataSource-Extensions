package com.caplin.integration.streamlinkx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.string.shouldContain
import java.security.KeyPairGenerator

class StreamLinkConnectionFactoryTest :
    FunSpec(
        {
          val factory =
              StreamLinkConnectionFactory(
                  "rttp://localhost:8080",
                  KeyPairGenerator.getInstance("RSA").generateKeyPair().private,
              )

          context("connect rejects usernames that would corrupt subject parsing") {
            withData(
                "user/name",
                "user?name",
                "/leading",
                "trailing?",
                "a/b?c",
            ) { username ->
              val exception = shouldThrow<IllegalArgumentException> { factory.connect(username) }
              exception.message shouldContain "not URL-safe"
            }
          }
        },
    )
