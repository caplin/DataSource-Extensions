package example.stubs

import java.time.Instant
import java.util.UUID

/**
 * Represents a chat message sent by a user.
 *
 * Each chat message contains a unique identifier, the ID of the user who sent the message, the
 * actual message content, the timestamp when the message was sent, and an optional timestamp
 * indicating the last time the message was modified.
 *
 * @param messageId The unique identifier of the chat message.
 * @param userId The ID of the user who sent the message.
 * @param message The content of the message.
 * @param timestamp The timestamp when the message was sent.
 */
data class ChatMessage(
    val messageId: UUID,
    val userId: String,
    val message: String,
    val timestamp: Instant
)
