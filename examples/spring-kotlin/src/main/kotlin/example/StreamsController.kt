package example

import com.caplin.integration.datasourcex.reactive.api.ContainerEvent
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent.Bulk
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent.RowEvent.Remove
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent.RowEvent.Upsert
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import com.caplin.integration.datasourcex.spring.annotations.IngressToken
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Controller
class StreamsController {

  /**
   * Streams an infinite sequence of [Payload] objects, where each payload contains an incrementing
   * version number (starting from 0), the [parameter1] and [parameter2] values extracted from the
   * path, and a randomly generated unique string identifier.
   *
   * The stream emits new elements at one-second intervals.
   *
   * @see Flow
   */
  @MessageMapping("/public/stream/{parameter1}/{parameter2}")
  fun genericStream(
      @DestinationVariable parameter1: String,
      @DestinationVariable parameter2: Int,
  ) = createPayloadFlow(null, null, parameter1, parameter2)

  /**
   * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
   * version number (starting from 0), the [userId], [parameter1] and [parameter2] values extracted
   * from the path, and a randomly generated unique string identifier.
   *
   * The stream emits new elements at one-second intervals.
   *
   * @see Flow
   */
  @MessageMapping("/user/{userId}/stream/{parameter1}/{parameter2}")
  fun userStream(
      @IngressDestinationVariable(IngressToken.USER_ID) userId: String,
      @DestinationVariable parameter1: String,
      @DestinationVariable parameter2: Int,
  ) = createPayloadFlow(null, userId, parameter1, parameter2)

  /**
   * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
   * version number (starting from 0), the [userId], [sessionId], [parameter1] and [parameter2]
   * values extracted from the path, and a randomly generated unique string identifier.
   *
   * The stream emits new elements at one-second intervals.
   *
   * @see Flow
   */
  @MessageMapping("/session/{userId}/{sessionId}/stream/{parameter1}/{parameter2}")
  fun sessionStream(
      @IngressDestinationVariable(IngressToken.USER_ID) userId: String,
      @IngressDestinationVariable(IngressToken.PERSISTENT_SESSION_ID) sessionId: String,
      @DestinationVariable parameter1: String,
      @DestinationVariable parameter2: Int,
  ) = createPayloadFlow(sessionId, userId, parameter1, parameter2)

  /**
   * Streams a sequence of [ContainerEvent]s of [Payload] objects.
   *
   * This example demonstrates how to publish a DataSource container. It initially emits a [Bulk]
   * event containing 10 rows (keys "0" through "9"). Subsequently, it emits random [Upsert] or
   * [Remove] events for these rows at one-second intervals.
   *
   * @see Flow
   */
  @MessageMapping("/public/container/{parameter1}/{parameter2}")
  fun containerStream(
      @DestinationVariable parameter1: String,
      @DestinationVariable parameter2: Int,
  ): Flow<ContainerEvent<Payload>> = createContainerFlow(null, parameter1, parameter2)

  /**
   * Streams a sequence of [ContainerEvent]s of [Payload] objects.
   *
   * This example demonstrates how to publish a DataSource container. It initially emits a [Bulk]
   * event containing 10 rows (keys "0" through "9"). Subsequently, it emits random [Upsert] or
   * [Remove] events for these rows at one-second intervals.
   *
   * @see Flow
   */
  @MessageMapping("/user/{userId}/container/{parameter1}/{parameter2}")
  fun userContainerStream(
      @IngressDestinationVariable(IngressToken.USER_ID) userId: String,
      @DestinationVariable parameter1: String,
      @DestinationVariable parameter2: Int,
  ): Flow<ContainerEvent<Payload>> = createContainerFlow(userId, parameter1, parameter2)

  private fun createContainerFlow(userId: String?, parameter1: String, parameter2: Int) = flow {
    val rows = mutableMapOf<Int, Int>()

    emit(
        Bulk(
            (0 until 10).map { row ->
              rows[row] = 1
              Upsert(
                  row.toString(),
                  Payload(
                      0,
                      userId,
                      null,
                      parameter1,
                      parameter2,
                      UUID.randomUUID(),
                  ),
              )
            },
        ),
    )

    while (true) {
      delay(1.seconds)
      val row = Random.nextInt(10)
      if (Random.nextInt(3) != 2) {
        val version = rows.getOrPut(row) { 0 }
        emit(
            Upsert(
                row.toString(),
                Payload(
                    version,
                    userId,
                    null,
                    parameter1,
                    parameter2,
                    UUID.randomUUID(),
                ),
            ),
        )
        rows[row] = version + 1
      } else {
        rows.remove(row)
        emit(Remove(row.toString()))
      }
    }
  }

  private fun createPayloadFlow(
      userId: String?,
      sessionId: String?,
      parameter1: String,
      parameter2: Int,
  ) = flow {
    var version = 0
    while (true) {
      emit(
          Payload(
              version = version++,
              userId = userId,
              sessionId = sessionId,
              parameter1 = parameter1,
              parameter2 = parameter2,
              uuid = UUID.randomUUID(),
          ),
      )
      delay(1.seconds)
    }
  }
}
