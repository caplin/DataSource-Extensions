package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent.RowEvent.Upsert
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping.Type.MAPPING
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping.Type.RECORD_GENERIC
import com.caplin.integration.datasourcex.spring.annotations.DataService
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import com.caplin.integration.datasourcex.spring.annotations.IngressToken.USER_ID
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.test.context.TestPropertySource

/**
 * End-to-end test of the Spring Boot starter: a real application context wires the
 * autoconfiguration, which binds the `@DataService` controller's `@DataMessageMapping` methods onto
 * a (faked) DataSource. Simulating peer requests drives the whole stack — message handler, argument
 * resolution, the controller, and the return-value handler — across every supported subject shape.
 */
@SpringBootTest(
    classes =
        [DataSourceEndToEndTest.TestConfig::class, DataSourceEndToEndTest.TestController::class]
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

    test("active JSON subject publishes the controller's flow") {
      fake.request("/price/USD")
      eventually(TIMEOUT) {
        fake.publishedJson shouldContain ("/price/USD" to mapOf("ccy" to "USD", "bid" to "1.23"))
      }
    }

    test("active generic-record subject publishes the controller's flow as record fields") {
      fake.request("/record/EUR")
      eventually(TIMEOUT) {
        fake.publishedRecords shouldContain
            ("/record/EUR" to mapOf("ccy" to "EUR", "bid" to "1.10"))
      }
    }

    test("active mapping subject publishes the remapped subject") {
      fake.request("/map/abc")
      eventually(TIMEOUT) { fake.publishedMappings shouldContain ("/map/abc" to "/real/abc") }
    }

    test("a streaming subject publishes each update in order then NotFound on completion") {
      fake.request("/ticks")
      eventually(TIMEOUT) {
        fake.publishedJson.filter { it.first == "/ticks" }.map { it.second } shouldBe
            listOf(mapOf("seq" to "1"), mapOf("seq" to "2"), mapOf("seq" to "3"))
        fake.erroredSubjects shouldContain "/ticks"
      }
    }

    test("a failing flow publishes a SubjectError and nothing else") {
      fake.request("/broken")
      eventually(TIMEOUT) { fake.erroredSubjects shouldContain "/broken" }
      fake.publishedJson.any { it.first == "/broken" } shouldBe false
    }

    test("a container subject publishes element structure and serves row data on request") {
      fake.request("/book/X")
      eventually(TIMEOUT) {
        fake.publishedContainers shouldContain ("/book/X" to listOf("/book/X-items/1"))
      }

      fake.request("/book/X-items/1")
      eventually(TIMEOUT) {
        fake.publishedRecords shouldContain ("/book/X-items/1" to mapOf("px" to "100"))
      }
    }

    test("a request-stream channel returns a response derived from the client's message") {
      val responses = fake.openChannel("/echo/42")
      fake.sendToChannel("/echo/42", mapOf("ping" to "1"))
      eventually(TIMEOUT) { responses shouldContain mapOf("ping" to "1", "echoedBy" to "42") }
    }

    test("a bidirectional channel responds to each client message") {
      val responses = fake.openChannel("/chat/7")
      fake.sendToChannel("/chat/7", mapOf("m" to "a"))
      fake.sendToChannel("/chat/7", mapOf("m" to "b"))
      eventually(TIMEOUT) {
        responses shouldContain mapOf("m" to "a", "by" to "7")
        responses shouldContain mapOf("m" to "b", "by" to "7")
      }
    }

    test("a fire-and-forget channel returns an OK acknowledgement") {
      val responses = fake.openChannel("/submit")
      fake.sendToChannel("/submit", mapOf("x" to "1"))
      eventually(TIMEOUT) { responses shouldContain mapOf("Result" to "ok") }
    }
  }

  @DataService
  class TestController {
    @DataMessageMapping("/price/{ccy}")
    fun price(@IngressDestinationVariable(token = USER_ID, value = "ccy") ccy: String): Flow<Any> =
        flowOf(mapOf("ccy" to ccy, "bid" to "1.23"))

    @DataMessageMapping("/record/{ccy}", type = RECORD_GENERIC)
    fun record(
        @IngressDestinationVariable(token = USER_ID, value = "ccy") ccy: String
    ): Flow<Map<String, String>> = flowOf(mapOf("ccy" to ccy, "bid" to "1.10"))

    @DataMessageMapping("/map/{id}", type = MAPPING)
    fun map(@IngressDestinationVariable(token = USER_ID, value = "id") id: String): Flow<String> =
        flowOf("/real/$id")

    @DataMessageMapping("/ticks")
    fun ticks(): Flow<Any> = flowOf(mapOf("seq" to "1"), mapOf("seq" to "2"), mapOf("seq" to "3"))

    @DataMessageMapping("/broken") fun broken(): Flow<Any> = flow { error("boom") }

    @DataMessageMapping("/book/{id}", type = RECORD_GENERIC)
    fun book(
        @IngressDestinationVariable(token = USER_ID, value = "id") id: String
    ): Flow<ContainerEvent<Map<String, String>>> = flow {
      emit(Upsert("1", mapOf("px" to "100")))
      awaitCancellation()
    }

    @DataMessageMapping("/echo/{id}")
    fun echo(
        @IngressDestinationVariable(token = USER_ID, value = "id") id: String,
        @Payload request: Map<String, String>,
    ): Flow<Map<String, String>> = flowOf(request + ("echoedBy" to id))

    @DataMessageMapping("/chat/{id}")
    fun chat(
        @IngressDestinationVariable(token = USER_ID, value = "id") id: String,
        @Payload messages: Flow<Map<String, String>>,
    ): Flow<Map<String, String>> = messages.map { it + ("by" to id) }

    @DataMessageMapping("/submit")
    fun submit(@Payload request: Map<String, String>) {
      // fire-and-forget: side effects only; the starter returns an OK acknowledgement
    }
  }

  @Configuration(proxyBeanMethods = false)
  class TestConfig {
    private val fake = FakeDataSource()

    @Bean fun fakeDataSource(): FakeDataSource = fake

    @Bean fun dataSource(): DataSource = fake.dataSource

    @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper()
  }

  private companion object {
    val TIMEOUT = 5.seconds
  }
}
