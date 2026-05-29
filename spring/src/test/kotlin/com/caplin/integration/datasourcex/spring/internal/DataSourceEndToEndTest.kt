package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping
import com.caplin.integration.datasourcex.spring.annotations.DataService
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import com.caplin.integration.datasourcex.spring.annotations.IngressToken
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource

/**
 * End-to-end test of the Spring Boot starter: a real application context wires the
 * autoconfiguration, which binds the `@DataService` controller's `@DataMessageMapping` onto a
 * (faked) DataSource. Simulating a peer request for a subject should drive the whole stack —
 * message handler, argument resolution, the controller, and the return-value handler — and publish
 * the controller's flow.
 */
@SpringBootTest(
    classes = [DataSourceEndToEndTest.TestConfig::class, DataSourceEndToEndTest.FxController::class]
)
@ImportAutoConfiguration(
    DataSourceAutoConfiguration::class,
    DataSourceServerAutoConfiguration::class,
)
@TestPropertySource(properties = ["caplin.datasource.managed.discovery.hostname=localhost"])
class DataSourceEndToEndTest : FunSpec() {

  @Autowired private lateinit var fake: FakeDataSource

  init {
    extension(SpringExtension())

    test("requesting an active JSON subject publishes the controller's flow") {
      fake.request("/price/USD")

      eventually(5.seconds) {
        fake.publishedJson shouldContain
            ("/price/USD" to mapOf("ccy" to "USD", "bid" to "1.23"))
      }
    }
  }

  @DataService
  class FxController {
    @DataMessageMapping("/price/{ccy}")
    fun price(
        @IngressDestinationVariable(token = IngressToken.USER_ID, value = "ccy") ccy: String
    ): Flow<Any> = flowOf(mapOf("ccy" to ccy, "bid" to "1.23"))
  }

  @Configuration(proxyBeanMethods = false)
  class TestConfig {
    private val fake = FakeDataSource()

    @Bean fun fakeDataSource(): FakeDataSource = fake

    @Bean fun dataSource(): DataSource = fake.dataSource

    @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper()
  }
}
