package example

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.LifecycleDataSource
import com.caplin.integration.datasourcex.util.Subject
import com.caplin.integration.kotest.LiberatorContainerExtension
import com.caplin.integration.streamlinkx.StreamLinkConnection.Companion.getSubject
import com.caplin.integration.streamlinkx.filterIsUpdate
import com.caplin.integration.streamlinkx.runningFold
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeout
import org.springframework.beans.factory.getBean
import org.springframework.boot.builder.SpringApplicationBuilder

/**
 * End-to-end test: starts a Liberator Testcontainer, runs the `spring-kotlin` example adapter
 * against it (pointed at the container's mapped DataSource port purely by a startup property) and
 * subscribes as a StreamLink client, proving the Spring starter serves data through a live
 * Liberator. Liberator uses dynamic peers/services, so it auto-discovers the adapter with no static
 * routing config.
 */
class SpringKotlinIntegrationTest :
    FunSpec(
        {
          val liberator = install(LiberatorContainerExtension())

          // Run the example unchanged, overriding only the outgoing peer to reach the container.
          // Passed as a command-line arg so it outranks the example's application.properties.
          val application =
              SpringApplicationBuilder(SpringKotlinApplication::class.java)
                  .run(
                      "--caplin.datasource.managed.peer.outgoing=" +
                          "ws://${liberator.containerState.host}:${liberator.dataSourcePort}",
                  )
          autoClose(AutoCloseable { application.close() })

          val dataSource = application.getBean<LifecycleDataSource>()

          val streamLink = liberator.connect("alice")
          autoClose(streamLink)

          beforeSpec {
            dataSource.awaitConnected()
            streamLink.awaitConnected()
            // Gate on the adapter being discovered end-to-end: the first public update only arrives
            // once the adapter has connected out to Liberator and registered its namespaces. Retry
            // across the startup race — a subscription before the adapter registers errors out.
            withTimeout(60.seconds) {
              while (
                  streamLink
                      .getSubject<Payload>(Subject("public", "stream", "ready", "0"))
                      .filterIsUpdate()
                      .firstOrNull() == null
              ) {
                delay(500.milliseconds)
              }
            }
          }

          context("a client subscribed to the spring-kotlin adapter") {
            test("serves the public payload stream end-to-end") {
              streamLink
                  .getSubject<Payload>(Subject("public", "stream", "foo", "42"))
                  .filterIsUpdate()
                  .test(timeout = 30.seconds) {
                    awaitItem().run {
                      version shouldBe 0
                      parameter1 shouldBe "foo"
                      parameter2 shouldBe 42
                      userId shouldBe null
                      sessionId shouldBe null
                      uuid.shouldNotBeNull()
                    }
                    awaitItem().version shouldBe 1
                    cancelAndIgnoreRemainingEvents()
                  }
            }

            test("serves the public container's initial bulk of 10 rows") {
              streamLink
                  .getContainer(Subject("public", "container", "foo", "42"))
                  .filterIsUpdate()
                  .runningFold()
                  .test(timeout = 30.seconds) {
                    var rows = awaitItem()
                    while (rows.size < 10) rows = awaitItem()
                    rows shouldHaveSize 10
                    cancelAndIgnoreRemainingEvents()
                  }
            }

            test("threads the injected USER_ID into the payload") {
              // The client omits the username segment; Liberator injects %u (the authenticated
              // user) via the object map the starter registered from @IngressDestinationVariable.
              streamLink
                  .getSubject<Payload>(Subject("user", "stream", "foo", "42"))
                  .filterIsUpdate()
                  .test(timeout = 30.seconds) {
                    awaitItem().run {
                      userId shouldBe "alice"
                      parameter1 shouldBe "foo"
                      parameter2 shouldBe 42
                    }
                    cancelAndIgnoreRemainingEvents()
                  }
            }

            test("threads the injected USER_ID and persistent-session id into the payload") {
              // Both the username (%u) and the persistent-session id (%g) are injected by
              // Liberator.
              streamLink
                  .getSubject<Payload>(Subject("session", "stream", "foo", "42"))
                  .filterIsUpdate()
                  .test(timeout = 30.seconds) {
                    awaitItem().run {
                      userId shouldBe "alice"
                      sessionId.shouldNotBeNull().shouldNotBeBlank()
                    }
                    cancelAndIgnoreRemainingEvents()
                  }
            }
          }
        },
    )
