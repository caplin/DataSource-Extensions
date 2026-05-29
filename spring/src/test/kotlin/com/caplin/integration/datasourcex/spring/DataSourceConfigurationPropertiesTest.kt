package com.caplin.integration.datasourcex.spring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.core.io.Resource

class DataSourceConfigurationPropertiesTest :
    FunSpec({
      val discovery = DataSourceConfigurationProperties.Discovery(hostname = "localhost")

      test("rejects specifying both provided and managed") {
        shouldThrow<IllegalStateException> {
          DataSourceConfigurationProperties(
              provided = DataSourceConfigurationProperties.Provided(mockk<Resource>(), null),
              managed = managed(discovery = discovery),
          )
        }
      }

      test("allows provided alone") {
        val props =
            DataSourceConfigurationProperties(
                provided = DataSourceConfigurationProperties.Provided(mockk<Resource>(), null),
                managed = null,
            )
        props.provided.shouldNotBeNull()
        props.managed shouldBe null
      }

      test("allows managed alone") {
        DataSourceConfigurationProperties(provided = null, managed = managed(discovery = discovery))
            .managed
            ?.discovery
            ?.hostname shouldBe "localhost"
      }

      test("Discovery defaults the cluster name") {
        DataSourceConfigurationProperties.Discovery(hostname = "h").clusterName shouldBe "caplin"
      }

      test("Managed rejects specifying both discovery and peer") {
        shouldThrow<IllegalStateException> {
          managed(
              discovery = discovery,
              peer = DataSourceConfigurationProperties.Peer(incoming = null),
          )
        }
      }

      test("Managed rejects specifying neither discovery nor peer") {
        shouldThrow<IllegalStateException> { managed(discovery = null, peer = null) }
      }
    })

private fun managed(
    discovery: DataSourceConfigurationProperties.Discovery? = null,
    peer: DataSourceConfigurationProperties.Peer? = null,
) =
    DataSourceConfigurationProperties.Managed(
        name = null,
        discovery = discovery,
        peer = peer,
        logDirectory = null,
        extraConfigurationFile = null,
    )
