package com.caplin.integration.streamlinkx

import app.cash.turbine.test
import com.caplin.integration.datasourcex.util.Subject
import com.caplin.integration.kotest.LiberatorContainerExtension
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey

class StreamLinkTest :
    FunSpec(
        {
          val liberator = install(LiberatorContainerExtension())

          context("As admin user") {
            val streamLink = liberator.connect("admin")
            streamLink.connect()
            streamLink.awaitConnected()

            test("Can fetch /SYSTEM/INFO") {
              streamLink.getSubject(Subject("SYSTEM", "INFO")).filterIsUpdate().test {
                awaitItem() shouldContainKey "NodeID"
              }
            }

            streamLink.disconnect()
          }
        },
    )
