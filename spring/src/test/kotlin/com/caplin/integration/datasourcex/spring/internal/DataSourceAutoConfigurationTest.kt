package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import com.caplin.datasource.messaging.json.JsonHandler
import com.caplin.integration.datasourcex.util.serialization.jackson3.Jackson3JsonHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.ObjectMapper as Jackson3ObjectMapper
import tools.jackson.databind.json.JsonMapper

/**
 * Verifies the autoconfiguration selects the Jackson 3 [JsonHandler] when a Jackson 3
 * [ObjectMapper] is present — the Spring Boot 4 default. [ImportAutoConfiguration] loads the
 * autoconfiguration with proper ordering so its `@ConditionalOnMissingBean` conditions see the
 * test's beans first, letting us mock the DataSource rather than create a real one.
 */
@SpringBootTest(classes = [DataSourceAutoConfigurationTest.TestConfig::class])
@ImportAutoConfiguration(DataSourceAutoConfiguration::class)
@TestPropertySource(properties = ["caplin.datasource.managed.discovery.hostname=localhost"])
class DataSourceAutoConfigurationTest : FunSpec() {

  @Autowired private lateinit var jsonHandler: JsonHandler<*>

  init {
    extension(SpringExtension())

    test("wires the Jackson 3 JsonHandler by default") {
      jsonHandler.shouldBeInstanceOf<Jackson3JsonHandler>()
    }
  }

  @Configuration(proxyBeanMethods = false)
  class TestConfig {
    @Bean fun jackson3ObjectMapper(): Jackson3ObjectMapper = JsonMapper.builder().build()

    @Bean fun dataSource(): DataSource = mockk(relaxed = true)
  }
}
