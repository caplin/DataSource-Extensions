package example

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.spring.annotations.IngressDestinationVariable
import com.caplin.integration.datasourcex.spring.annotations.IngressToken
import example.stubs.ChatService
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

/** Provides the bindings between the [ChatService] and [DataSource]. */
@Controller
class ChatController(
    private val chatService: ChatService,
) {
  data class PostPayload(val roomId: String, val message: String)

  data class Message(
      val userId: String,
      val message: String,
      val timestamp: Instant,
  )

  data class RoomInfo(
      val messageCount: Int,
      val uniqueUsersCount: Long,
  )

  /**
   * Returns the current information about the requested [roomId] and publishes an update each time
   * it changes.
   *
   * @see RoomInfo
   */
  @MessageMapping("/info/{roomId}")
  fun info(@DestinationVariable roomId: String): Flow<RoomInfo> =
      callbackFlow {
            val handle = chatService.addInfoListener(roomId) { trySendBlocking(it) }
            awaitClose { chatService.removeInfoListener(handle) }
          }
          .map { RoomInfo(it.messageCount, it.uniqueUsersCount) }

  /**
   * Returns the message for the requested [roomId] and [messageId].
   *
   * @see Message
   */
  @MessageMapping("/get/{roomId}/{messageId}")
  suspend fun get(
      @DestinationVariable roomId: String,
      @DestinationVariable messageId: UUID,
  ): Message =
      chatService.getMessage(roomId, messageId).await().let {
        Message(it.userId, it.message, it.timestamp)
      }

  /**
   * Returns the last 10 messages for the requested [roomId] and publishes an update each time a new
   * message is posted.
   *
   * @see Message
   */
  @MessageMapping("/tail/{roomId}")
  fun tail(@DestinationVariable roomId: String): Flow<List<Message>> =
      callbackFlow {
            val handle = chatService.addMessageListener(roomId) { trySendBlocking(it) }
            awaitClose { chatService.removeMessageListener(handle) }
          }
          .map { messageIds ->
            messageIds
                .asFlow()
                .map { messageId -> chatService.getMessage(roomId, messageId).await() }
                .map { Message(it.userId, it.message, it.timestamp) }
                .toList()
          }
          .scan(emptyList<Message>()) { emitted, toEmit -> (emitted + toEmit).takeLast(10) }
          .drop(1)

  /**
   * Posts a message to a room and responds with the created message's ID. The [userId] is extracted
   * from the user's authorization token.
   *
   * @see PostPayload
   */
  @MessageMapping("/post/{userId}")
  suspend fun post(
      @IngressDestinationVariable(token = IngressToken.USER_ID) userId: String,
      @Payload payload: PostPayload,
  ): UUID {
    val messageId = chatService.sendMessage(payload.roomId, userId, payload.message).await()
    return messageId
  }
}
