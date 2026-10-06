package com.caplin.integration.datasourcex.reactive.kotlin

import com.caplin.datasource.DataSource
import com.caplin.datasource.Service
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.emptyFlow

class ObjectMapOrderTest :
    FunSpec({
      val service = mockk<Service>(relaxed = true)

      beforeTest {
        mockkStatic(Service::class)
        every { Service.named(any()) } returns service
      }

      afterTest { unmockkStatic(Service::class) }

      // Liberator applies the first object map that matches, and a map's wildcards span `/`, so the
      // plain channel's map, bound first, would otherwise take the on-behalf-of channel's subjects.
      test(
          "registers a service's more specific object map first, whatever order they were bound in"
      ) {
        val dataSource = mockk<DataSource>(relaxed = true)
        dataSource.bind(scope = backgroundScope) {
          to("calendar") {
            active {
              record {
                namespace(
                    AntPatternNamespace("/CALENDAR/{username}/TENORDATES/{productPair}"),
                    { objectMappings = mapOf("username" to "%U") },
                ) {
                  emptyFlow()
                }
                namespace(
                    AntPatternNamespace(
                        "/CALENDAR/{username}/TENORDATES/TOBOUSER/{toboUser}/{productPair}"
                    ),
                    { objectMappings = mapOf("username" to "%u") },
                ) {
                  emptyFlow()
                }
              }
            }
          }
        }

        verifyOrder {
          service.addObjectMap(
              "/CALENDAR/TENORDATES/TOBOUSER/%1/%2",
              "/CALENDAR/%u/TENORDATES/TOBOUSER/%1/%2",
          )
          service.addObjectMap("/CALENDAR/TENORDATES/%1", "/CALENDAR/%U/TENORDATES/%1")
        }
      }
    })
