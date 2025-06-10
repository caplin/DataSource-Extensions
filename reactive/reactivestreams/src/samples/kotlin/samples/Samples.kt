package samples

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.reactive.api.RecordType
import com.caplin.integration.datasourcex.reactive.reactivestreams.Bind
import java.time.Duration
import reactor.core.publisher.Flux

class Samples {
  fun bind(dataSource: DataSource) {
    Bind.using(dataSource) {
      to("my-service") {
        active {
          record {
            path(
                path = "/example/activeSubject",
                publisher =
                    Flux.interval(Duration.ofSeconds(1)).map { aLong: Long ->
                      mapOf("Count" to aLong.toString())
                    },
            ) {
              recordType = RecordType.TYPE1
            }
          }

          json {
            pattern(
                pattern = "/example/{username}/subjectPattern/{myKey}",
                configure = { objectMappings = mapOf("username" to "%u") },
                supplier = { _, parameters ->
                  val username = parameters["username"]
                  val myKey = parameters["myKey"]
                  Flux.interval(Duration.ofSeconds(1)).map { count: Long ->
                    JsonObject("$username $myKey $count")
                  }
                },
            )
          }
        }
      }
    }
  }
}
