package example;

import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable;
import com.caplin.integration.datasourcex.spring.annotations.IngressToken;
import java.time.Duration;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class StreamsController {

    /**
     * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
     * version number (starting from 0), the parameter1 & parameter2 values extracted from the
     * path, and a randomly generated unique string identifier.
     * <br>
     * The stream emits new elements at one-second intervals.
     *
     * @see Flux
     */
    @MessageMapping("/public/stream/{parameter1}/{parameter2}")
    public Flux<Payload> genericStream(
        @DestinationVariable String parameter1,
        @DestinationVariable int parameter2
    ) {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(1)).map(version ->
            new Payload(version.intValue(), null, null, parameter1, parameter2, UUID.randomUUID())
        );
    }

    /**
     * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
     * version number (starting from 0), the user ID, parameter1 & parameter2 values extracted
     * from the path, and a randomly generated unique string identifier.
     * <br>
     * The stream emits new elements at one-second intervals.
     *
     * @see Flux
     */
    @MessageMapping("/user/{userId}/stream/{parameter1}/{parameter2}")
    public Flux<Payload> userStream(
        @IngressDestinationVariable(token = IngressToken.USER_ID) String userId,
        @DestinationVariable String parameter1,
        @DestinationVariable int parameter2
    ) {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(1)).map(version ->
            new Payload(version.intValue(), userId, null, parameter1, parameter2, UUID.randomUUID())
        );
    }

    /**
     * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
     * version number (starting from 0), the user ID, session ID, parameter1 & parameter2
     * values extracted from the path, and a randomly generated unique string identifier.
     * <br>
     * The stream emits new elements at one-second intervals.
     *
     * @see Flux
     */
    @MessageMapping("/session/{userId}/{sessionId}/stream/{parameter1}/{parameter2}")
    public Flux<Payload> sessionStream(
        @IngressDestinationVariable(token = IngressToken.USER_ID) String userId,
        @IngressDestinationVariable(token = IngressToken.PERSISTENT_SESSION_ID) String sessionId,
        @DestinationVariable String parameter1,
        @DestinationVariable int parameter2
    ) {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(1)).map(version ->
            new Payload(version.intValue(), userId, sessionId, parameter1, parameter2, UUID.randomUUID())
        );
    }
}

