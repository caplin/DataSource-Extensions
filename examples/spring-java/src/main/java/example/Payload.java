package example;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record Payload(
    int version,
    @Nullable String userId,
    @Nullable String sessionId,
    String parameter1,
    int parameter2,
    UUID uuid) {
}
