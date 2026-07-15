package samples

import com.caplin.integration.datasourcex.util.Subject
import com.caplin.integration.kotest.LiberatorContainerExtension
import com.caplin.integration.streamlinkx.filterIsUpdate
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.flow.first

class Samples {

  fun spec() =
      object :
          FunSpec(
              {
                // Starts a Liberator in a Testcontainers container for the lifetime of the spec.
                val liberator = install(LiberatorContainerExtension())

                test("fetches a record from the containerised Liberator") {
                  val connection = liberator.connect("admin")
                  connection.connect()
                  connection.awaitConnected()

                  val info =
                      connection.getSubject(Subject("SYSTEM", "INFO")).filterIsUpdate().first()
                  println(info)

                  connection.disconnect()
                }
              },
          ) {}
}
