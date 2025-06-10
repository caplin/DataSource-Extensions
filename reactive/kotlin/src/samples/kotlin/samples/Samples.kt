package samples

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.reactive.kotlin.bind
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class Samples {

  fun bind(dataSource: DataSource) {
    dataSource.bind {
      to("my-service") {
        active {
          record {
            path(
                path = "/example/activeSubject",
                publisher =
                    flow {
                      var count = 0
                      while (true) {
                        emit(mapOf("Count" to "${count++}"))
                        delay(1.seconds)
                      }
                    },
            )
          }

          json {
            pattern(
                pattern = "/{username}/example/subjectPattern/{myKey}",
                configure = { objectMappings = mapOf("username" to "%u") },
            ) { _, parameters ->
              val username: String by parameters
              val myKey: String by parameters
              flow {
                var count = 0
                while (true) {
                  emit(JsonObject("$username $myKey ${count++}"))
                  delay(1.seconds)
                }
              }
            }
          }
        }
      }
    }
  }
}
