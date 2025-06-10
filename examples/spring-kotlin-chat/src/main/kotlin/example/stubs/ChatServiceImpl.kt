package example.stubs

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Consumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class ChatServiceImpl : ChatService {
  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  private val roomMessages = mutableMapOf<String, LinkedHashMap<UUID, ChatMessage>>()

  private val roomListeners = mutableMapOf<String, MutableMap<UUID, Consumer<Collection<UUID>>>>()
  private val infoListeners = mutableMapOf<String, MutableMap<UUID, Consumer<RoomInfo>>>()
  private val messageListenerRooms = mutableMapOf<UUID, String>()
  private val infoListenerRooms = mutableMapOf<UUID, String>()

  override fun addInfoListener(roomId: String, listener: Consumer<RoomInfo>): UUID {
    val handle = UUID.randomUUID()
    executor.submit {
      val listeners = getInfoListeners(roomId)
      infoListenerRooms.put(handle, roomId)
      listeners.put(handle, listener)
      val messages = getMessages(roomId)
      val roomInfo =
          RoomInfo(
              messages.values.size,
              messages.values.stream().map<Any>(ChatMessage::userId).distinct().count(),
          )
      listener.accept(roomInfo)
    }
    return handle
  }

  override fun removeInfoListener(handle: UUID) {
    executor.execute {
      val roomId =
          checkNotNull(infoListenerRooms.remove(handle)) { "No room found for handle $handle" }
      getMessageListeners(roomId).remove(handle)
    }
  }

  override fun addMessageListener(roomId: String, listener: Consumer<Collection<UUID>>): UUID {
    val handle = UUID.randomUUID()
    executor.submit {
      val listeners = getMessageListeners(roomId)
      messageListenerRooms.put(handle, roomId)
      listeners.put(handle, listener)
      val messages = getMessages(roomId)
      listener.accept(messages.keys)
    }
    return handle
  }

  override fun removeMessageListener(handle: UUID) {
    executor.execute {
      val roomId =
          checkNotNull(messageListenerRooms.remove(handle)) { "No room found for handle $handle" }
      getMessageListeners(roomId).remove(handle)
    }
  }

  override fun getMessage(roomId: String, messageId: UUID): CompletableFuture<ChatMessage> {
    return CompletableFuture.supplyAsync(
        { getMessages(roomId).get(messageId) },
        executor,
    )
  }

  override fun sendMessage(
      roomId: String,
      userId: String,
      message: String
  ): CompletableFuture<UUID> {
    return CompletableFuture.supplyAsync(
        {
          val messageId = UUID.randomUUID()
          val chatMessage = ChatMessage(messageId, userId, message, Instant.now())
          val messages = getMessages(roomId)
          messages.put(messageId, chatMessage)

          logger.info("$roomId received message $chatMessage")

          val roomInfo =
              RoomInfo(
                  messages.values.size,
                  messages.values.stream().map<Any>(ChatMessage::userId).distinct().count(),
              )
          getInfoListeners(roomId)
              .values
              .forEach(
                  Consumer { roomInfoConsumer: Consumer<RoomInfo> ->
                    roomInfoConsumer.accept(
                        roomInfo,
                    )
                  },
              )

          getMessageListeners(roomId)
              .values
              .forEach(
                  Consumer { chatListener: Consumer<Collection<UUID>> ->
                    chatListener.accept(
                        setOf(messageId),
                    )
                  },
              )
          messageId
        },
        executor,
    )
  }

  private fun getMessageListeners(roomId: String): MutableMap<UUID, Consumer<Collection<UUID>>> {
    return roomListeners.computeIfAbsent(roomId) { s: String -> mutableMapOf() }
  }

  private fun getInfoListeners(roomId: String): MutableMap<UUID, Consumer<RoomInfo>> {
    return infoListeners.computeIfAbsent(roomId) { s: String -> mutableMapOf() }
  }

  private fun getMessages(roomId: String): LinkedHashMap<UUID, ChatMessage> {
    return roomMessages.computeIfAbsent(roomId) { s: String ->
      object : LinkedHashMap<UUID, ChatMessage>() {
        init {
          val uuid = UUID.randomUUID()
          put(
              uuid,
              ChatMessage(
                  uuid,
                  "bot",
                  "Welcome to the start of the $roomId room!",
                  Instant.now(),
              ),
          )
        }
      }
    }
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(ChatServiceImpl::class.java)
  }
}
