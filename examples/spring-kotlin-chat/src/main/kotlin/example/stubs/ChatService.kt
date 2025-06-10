package example.stubs

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

interface ChatService {
  /**
   * Adds a room info listener to the chat room with the specified ID.
   *
   * The listener will be immediately invoked with the current room info, and then will receive
   * updates as this info changes.
   *
   * @param roomId The unique identifier of the chat room.
   * @param listener The listener to be added.
   * @return The unique identifier of the newly added listener.
   */
  fun addInfoListener(roomId: String, listener: Consumer<RoomInfo>): UUID

  /**
   * Removes a room info listener from the chat room.
   *
   * @param handle The unique identifier of the listener to be removed.
   */
  fun removeInfoListener(handle: UUID)

  /**
   * Adds a chat message ID listener to the chat room with the specified ID.
   *
   * The listener will be immediately invoked with a list of all existing chat message IDs, and then
   * will receive new messages IDs as they are entered.
   *
   * @param roomId The unique identifier of the chat room.
   * @param listener The listener to be added.
   * @return The unique identifier of the newly added listener.
   */
  fun addMessageListener(roomId: String, listener: Consumer<Collection<UUID>>): UUID

  /**
   * Removes a chat message ID listener from the chat room.
   *
   * @param handle The unique identifier of the listener to be removed.
   */
  fun removeMessageListener(handle: UUID)

  /**
   * Retrieves a specific chat message from a chat room.
   *
   * @param roomId The unique identifier of the chat room.
   * @param messageId The unique identifier of the chat message to retrieve.
   * @return The ChatMessage object representing the specific chat message.
   */
  fun getMessage(roomId: String, messageId: UUID): CompletableFuture<ChatMessage>

  /**
   * Sends a message from a specific user in a chat room.
   *
   * @param roomId The unique identifier of the chat room.
   * @param userId The unique identifier of the user by whom the message was sent.
   * @param message The message to be sent.
   */
  fun sendMessage(roomId: String, userId: String, message: String): CompletableFuture<UUID>

  companion object {
    /**
     * Creates a new instance of ChatService.
     *
     * @return A new instance of ChatService.
     */
    fun create(): ChatService {
      return ChatServiceImpl()
    }
  }
}
