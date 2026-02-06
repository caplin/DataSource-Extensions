package example;

import static com.caplin.integration.datasourcex.reactive.api.ContainerEvent.RowEvent;

import com.caplin.integration.datasourcex.reactive.api.ContainerEvent;
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable;
import com.caplin.integration.datasourcex.spring.annotations.IngressToken;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class StreamsController {

  /**
   * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
   * version number (starting from 0), the parameter1 & parameter2 values extracted from the path,
   * and a randomly generated unique string identifier. <br>
   * The stream emits new elements at one-second intervals.
   *
   * @see Flux
   */
  @MessageMapping("/public/stream/{parameter1}/{parameter2}")
  public Flux<Payload> genericStream(
      @DestinationVariable String parameter1, @DestinationVariable int parameter2) {
    return createPayloadFlux(null, null, parameter1, parameter2);
  }

  /**
   * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
   * version number (starting from 0), the user ID, parameter1 & parameter2 values extracted from
   * the path, and a randomly generated unique string identifier. <br>
   * The stream emits new elements at one-second intervals.
   *
   * @see Flux
   */
  @MessageMapping("/user/{userId}/stream/{parameter1}/{parameter2}")
  public Flux<Payload> userStream(
      @IngressDestinationVariable(token = IngressToken.USER_ID) String userId,
      @DestinationVariable String parameter1,
      @DestinationVariable int parameter2) {
    return createPayloadFlux(userId, null, parameter1, parameter2);
  }

  /**
   * Streams an infinite sequence of Payload objects, where each payload contains an incrementing
   * version number (starting from 0), the user ID, session ID, parameter1 & parameter2 values
   * extracted from the path, and a randomly generated unique string identifier. <br>
   * The stream emits new elements at one-second intervals.
   *
   * @see Flux
   */
  @MessageMapping("/session/{userId}/{sessionId}/stream/{parameter1}/{parameter2}")
  public Flux<Payload> sessionStream(
      @IngressDestinationVariable(token = IngressToken.USER_ID) String userId,
      @IngressDestinationVariable(token = IngressToken.PERSISTENT_SESSION_ID) String sessionId,
      @DestinationVariable String parameter1,
      @DestinationVariable int parameter2) {
    return createPayloadFlux(userId, sessionId, parameter1, parameter2);
  }

  /**
   * Streams a sequence of {@link ContainerEvent}s of {@link Payload} objects.
   *
   * <p>This example demonstrates how to publish a DataSource container. It initially emits a {@link
   * ContainerEvent.Bulk} event containing 10 rows (keys "0" through "9"). Subsequently, it emits
   * random {@link RowEvent.Upsert} or {@link RowEvent.Remove} events for these rows at one-second
   * intervals.
   *
   * @see Flux
   */
  @MessageMapping("/public/container/{parameter1}/{parameter2}")
  public Flux<ContainerEvent<Payload>> containerStream(
      @DestinationVariable String parameter1, @DestinationVariable int parameter2) {
    return createContainerFlux(null, parameter1, parameter2);
  }

  /**
   * Streams a sequence of {@link ContainerEvent}s of {@link Payload} objects.
   *
   * <p>This example demonstrates how to publish a user-specific DataSource container. It initially
   * emits a {@link ContainerEvent.Bulk} event containing 10 rows (keys "0" through "9").
   * Subsequently, it emits random {@link RowEvent.Upsert} or {@link RowEvent.Remove} events for
   * these rows at one-second intervals.
   *
   * @see Flux
   */
  @MessageMapping("/user/{userId}/container/{parameter1}/{parameter2}")
  public Flux<ContainerEvent<Payload>> userContainerStream(
      @IngressDestinationVariable(token = IngressToken.USER_ID) String userId,
      @DestinationVariable String parameter1,
      @DestinationVariable int parameter2) {
    return createContainerFlux(userId, parameter1, parameter2);
  }

  private Flux<Payload> createPayloadFlux(
      String userId, String sessionId, String parameter1, int parameter2) {
    return Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
        .map(
            version ->
                new Payload(
                    version.intValue(),
                    userId,
                    sessionId,
                    parameter1,
                    parameter2,
                    UUID.randomUUID()));
  }

  @SuppressWarnings("unchecked")
  private Flux<ContainerEvent<Payload>> createContainerFlux(
      String userId, String parameter1, int parameter2) {

    Map<Integer, Integer> rows = new ConcurrentHashMap<>();

    Flux<ContainerEvent<Payload>> initial =
        Flux.just(
            new ContainerEvent.Bulk<>(
                IntStream.rangeClosed(0, 9)
                    .mapToObj(
                        row -> {
                          rows.put(row, 1);
                          return new RowEvent.Upsert<>(
                              String.valueOf(row),
                              new Payload(
                                  0, userId, null, parameter1, parameter2, UUID.randomUUID()));
                        })
                    .collect(Collectors.toList())));

    Flux<ContainerEvent<Payload>> updates =
        Flux.interval(Duration.ofSeconds(1))
            .map(
                i -> {
                  int row = ThreadLocalRandom.current().nextInt(10);
                  if (ThreadLocalRandom.current().nextInt(3) != 2) {
                    int version = rows.getOrDefault(row, 0);
                    rows.put(row, version + 1);
                    return new RowEvent.Upsert<Payload>(
                        String.valueOf(row),
                        new Payload(
                            version, userId, null, parameter1, parameter2, UUID.randomUUID()));
                  } else {
                    rows.remove(row);
                    return new RowEvent.Remove(String.valueOf(row));
                  }
                });

    return Flux.concat(initial, updates);
  }
}
