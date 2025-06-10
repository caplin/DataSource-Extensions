package example

import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import com.caplin.integration.datasourcex.spring.annotations.IngressToken
import java.util.UUID
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
  ) = flow {
    var version = 0
    while (true) {
      emit(
          Payload(
              version = version++,
              userId = null,
              sessionId = null,
              parameter1 = parameter1,
              parameter2 = parameter2,
              uuid = UUID.randomUUID(),
          ),
      )
      delay(1.seconds)
    }
  }

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
  ) = flow {
    var version = 0
    while (true) {
      emit(
          Payload(
              version = version++,
              userId = null,
              sessionId = userId,
              parameter1 = parameter1,
              parameter2 = parameter2,
              uuid = UUID.randomUUID(),
          ),
      )
      delay(1.seconds)
    }
  }

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
  ) = flow {
    var version = 0
    while (true) {
      emit(
          Payload(
              version = version++,
              userId = sessionId,
              sessionId = userId,
              parameter1 = parameter1,
              parameter2 = parameter2,
              uuid = UUID.randomUUID(),
          ),
      )
      delay(1.seconds)
    }
  }
}
