package com.caplin.integration.streamlinkx

import com.caplin.integration.streamlinkx.StreamLinkConnectionFactory.Companion.convertDerToPem
import com.caplin.keymaster.KeyMasterHashingAlgorithm.SHA256
import com.caplin.keymaster.PEMPKCS8KeyMasterConfiguration
import com.caplin.streamlink.StreamLink
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.verify
import java.security.KeyPairGenerator

/**
 * Lifecycle of the connection [StreamLinkConnectionFactory.connect] returns, exercised against a
 * mocked [StreamLink] so no Liberator is needed.
 */
class StreamLinkConnectionLifecycleTest :
    FunSpec({
      fun factoryReturning(streamLink: StreamLink) =
          StreamLinkConnectionFactory.withStreamLink(
              "rttp://localhost:8080",
              PEMPKCS8KeyMasterConfiguration(
                  KeyPairGenerator.getInstance("RSA")
                      .generateKeyPair()
                      .private
                      .encoded
                      .convertDerToPem()
                      .byteInputStream(),
                  SHA256,
                  null,
              ),
          ) { _, _ ->
            streamLink
          }

      test("connect opens the StreamLink") {
        val streamLink = mockk<StreamLink>(relaxed = true)

        factoryReturning(streamLink).connect("admin")

        verify(exactly = 1) { streamLink.connect() }
      }

      test("disconnect closes the StreamLink") {
        val streamLink = mockk<StreamLink>(relaxed = true)

        factoryReturning(streamLink).connect("admin").disconnect()

        verify(exactly = 1) { streamLink.disconnect() }
      }

      test("close closes the StreamLink") {
        val streamLink = mockk<StreamLink>(relaxed = true)

        factoryReturning(streamLink).connect("admin").close()

        verify(exactly = 1) { streamLink.disconnect() }
      }

      test("disconnecting twice only closes the StreamLink once") {
        val streamLink = mockk<StreamLink>(relaxed = true)
        val connection = factoryReturning(streamLink).connect("admin")

        connection.disconnect()
        connection.close()

        verify(exactly = 1) { streamLink.disconnect() }
      }

      test("the returned connection is single-use") {
        val streamLink = mockk<StreamLink>(relaxed = true)
        val connection = factoryReturning(streamLink).connect("admin")

        val exception = shouldThrow<IllegalStateException> { connection.connect() }

        exception.message shouldContain "Create a new connection to reconnect."
        // The failed reconnect must not have reached the client.
        verify(exactly = 1) { streamLink.connect() }
      }

      test("a disconnected connection still refuses to reconnect") {
        val streamLink = mockk<StreamLink>(relaxed = true)
        val connection = factoryReturning(streamLink).connect("admin")
        connection.disconnect()

        shouldThrow<IllegalStateException> { connection.connect() }

        // Reconnecting must go through the factory, not revive a discarded client.
        verify(exactly = 1) { streamLink.connect() }
        verify(exactly = 1) { streamLink.disconnect() }
      }
    })
