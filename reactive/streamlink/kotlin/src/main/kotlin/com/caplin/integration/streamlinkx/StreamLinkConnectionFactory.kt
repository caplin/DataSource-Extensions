@file:OptIn(ExperimentalAtomicApi::class)

package com.caplin.integration.streamlinkx

import com.caplin.integration.datasourcex.util.Subject
import com.caplin.integration.datasourcex.util.Subject.Companion.path
import com.caplin.integration.streamlinkx.ContainerChange.Clear
import com.caplin.integration.streamlinkx.ContainerChange.RowChange.Added
import com.caplin.integration.streamlinkx.ContainerChange.RowChange.Removed
import com.caplin.keymaster.AuthenticationParameters
import com.caplin.keymaster.IKeyMasterConfiguration
import com.caplin.keymaster.KeyMaster
import com.caplin.keymaster.KeyMasterHashingAlgorithm
import com.caplin.keymaster.KeyMasterHashingAlgorithm.SHA256
import com.caplin.keymaster.PEMPKCS8KeyMasterConfiguration
import com.caplin.keymaster.StandardFormatter
import com.caplin.keymaster.permissioning.Permission
import com.caplin.keymaster.permissioning.UserPermissions
import com.caplin.streamlink.BaseConnectionListener
import com.caplin.streamlink.ChannelListener
import com.caplin.streamlink.ConnectionStatusEvent
import com.caplin.streamlink.ContainerElement
import com.caplin.streamlink.ContainerModel
import com.caplin.streamlink.CredentialsProvider
import com.caplin.streamlink.CredentialsReceiver
import com.caplin.streamlink.JsonChannel
import com.caplin.streamlink.JsonChannelListener
import com.caplin.streamlink.JsonEvent
import com.caplin.streamlink.JsonHandler
import com.caplin.streamlink.RecordType1Event
import com.caplin.streamlink.StreamLink
import com.caplin.streamlink.StreamLinkFactory
import com.caplin.streamlink.Subscription
import com.caplin.streamlink.SubscriptionErrorEvent
import com.caplin.streamlink.SubscriptionListener
import com.caplin.streamlink.SubscriptionStatusEvent
import com.caplin.streamlink.impl.credentials.CredentialsImpl
import com.flipkart.zjsonpatch.Jackson3JsonPatch
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.security.PrivateKey
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.encoding.Base64
import kotlin.reflect.KClass
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Creates authenticated [StreamLinkConnection]s to a Liberator.
 *
 * Construct one via the `invoke` factory operators in the companion object, supplying the Liberator
 * URL and a KeyMaster credential (a [PrivateKey], a PEM [InputStream], or a full
 * [IKeyMasterConfiguration]). Each call to [connect] mints a KeyMaster token for a user and opens a
 * fresh connection.
 *
 * @param liberator the Liberator URL to connect to (e.g. `rttp://host:port`).
 * @param keymasterConfiguration KeyMaster configuration used to sign authentication tokens.
 * @param objectMapper Jackson mapper used to (de)serialise JSON channel and record payloads.
 */
class StreamLinkConnectionFactory
private constructor(
    private val liberator: String,
    keymasterConfiguration: IKeyMasterConfiguration,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
  companion object {
    private const val CHUNK_SIZE = 64

    /**
     * Characters that must not appear in a username. Usernames are injected verbatim into subjects
     * (as a raw `%u`/`%U` path variable) and would corrupt subject parsing: `/` is the Ant path
     * separator and `?` starts the query portion.
     */
    private val UNSAFE_USERNAME_CHARS = setOf('/', '?')

    /**
     * Encodes these DER-encoded key bytes as a PEM string, wrapping the Base64 body in
     * `-----BEGIN/END [keyType]-----` markers.
     */
    fun ByteArray.convertDerToPem(keyType: String = "PRIVATE KEY"): String {
      val base64Encoded = Base64.encode(this)
      val formattedBase64 = base64Encoded.chunked(CHUNK_SIZE).joinToString("\n")
      return "-----BEGIN $keyType-----\n$formattedBase64\n-----END $keyType-----\n"
    }

    /**
     * Creates a factory using a KeyMaster [PrivateKey], which is converted to PEM and hashed with
     * [hashingAlgorithm] when signing tokens.
     */
    operator fun invoke(
        liberator: String,
        keymasterKey: PrivateKey,
        hashingAlgorithm: KeyMasterHashingAlgorithm = SHA256,
        objectMapper: ObjectMapper = jacksonObjectMapper(),
    ) =
        StreamLinkConnectionFactory(
            liberator,
            keymasterKey.encoded.convertDerToPem().byteInputStream(),
            hashingAlgorithm,
            objectMapper,
        )

    /**
     * Creates a factory reading a PKCS#8 PEM KeyMaster key from [keymasterKey], hashed with
     * [hashingAlgorithm] when signing tokens.
     */
    operator fun invoke(
        liberator: String,
        keymasterKey: InputStream,
        hashingAlgorithm: KeyMasterHashingAlgorithm = SHA256,
        objectMapper: ObjectMapper = jacksonObjectMapper(),
    ) =
        StreamLinkConnectionFactory(
            liberator,
            PEMPKCS8KeyMasterConfiguration(
                keymasterKey,
                hashingAlgorithm,
                null,
            ),
            objectMapper,
        )

    /** Creates a factory from a fully-formed [keymasterConfiguration]. */
    operator fun invoke(
        liberator: String,
        keymasterConfiguration: IKeyMasterConfiguration,
        objectMapper: ObjectMapper = jacksonObjectMapper(),
    ) = StreamLinkConnectionFactory(liberator, keymasterConfiguration, objectMapper)
  }

  private val kLogger = KotlinLogging.logger {}

  private val scope =
      CoroutineScope(
          SupervisorJob() +
              Dispatchers.Default +
              CoroutineExceptionHandler { _, throwable ->
                kLogger.error(throwable) { "Uncaught exception in StreamLink coroutine scope" }
              },
      )

  private val keymaster = KeyMaster(keymasterConfiguration)

  /**
   * Opens a new [StreamLinkConnection] authenticated as [username].
   *
   * A KeyMaster token granting full read/write permissions is generated for the user, and a
   * StreamLink client is created and connected to the configured Liberator. The returned connection
   * is single-use — [StreamLinkConnection.connect] on it will fail; create another connection to
   * reconnect.
   *
   * @throws IllegalArgumentException if [username] contains a character that would corrupt subject
   *   parsing (`/` or `?`).
   */
  fun connect(username: String): StreamLinkConnection {
    require(username.none { it in UNSAFE_USERNAME_CHARS }) {
      "Username '$username' contains characters that are not URL-safe; '/' and '?' are not allowed."
    }

    val connected = AtomicBoolean(false)

    val token =
        keymaster
            .generateToken(
                AuthenticationParameters(
                    username,
                    UserPermissions().apply { addPermission(Permission(".*", true, true, false)) },
                ),
                StandardFormatter(),
            )
            .let {
              val tokenPrefix = "token="
              it.lines().single { line -> line.startsWith(tokenPrefix) }.removePrefix(tokenPrefix)
            }

    val sl =
        StreamLinkFactory.create(
            StreamLinkFactory.createConfiguration().apply {
              setLiberatorUrlProvider { liberator }
              setJsonHandler(
                  object : JsonHandler {
                    override fun parse(value: String): Any = objectMapper.readValue<JsonNode>(value)

                    override fun patch(source: Any, diff: String): Any =
                        Jackson3JsonPatch.apply(
                            objectMapper.readValue<JsonNode>(diff),
                            source as JsonNode,
                        )

                    override fun format(value: Any): String = objectMapper.writeValueAsString(value)
                  },
              )
            },
            object : CredentialsProvider {
              override fun onCredentialsRequired(receiver: CredentialsReceiver) {
                receiver.provideCredentials(CredentialsImpl(username, token))
              }

              override fun cancel() {
                // no-op
              }
            },
        )

    sl.connect()

    val state =
        callbackFlow {
              val listener =
                  object : BaseConnectionListener() {
                    override fun onConnectionStatusChange(event: ConnectionStatusEvent) {
                      trySendBlocking(event).onFailure {
                        if (it !is CancellationException)
                            kLogger.error(it) { "Failed to send $event" }
                        if (it != null) throw it
                      }
                    }
                  }

              sl.addConnectionListener(listener)
              awaitClose { sl.removeConnectionListener(listener) }
            }
            .onStart { emit(sl.connectionCurrentState.connectionStatus) }
            .distinctUntilChanged()
            .shareIn(scope, Lazily, 1)

    return object : StreamLinkConnection, StreamLink by sl {

      override val username = username

      override val state = state

      override fun getChannel(
          subject: Subject,
          send: Flow<Map<String, String>>,
      ): Flow<RecordEvent> {
        val path = subject.path
        kLogger.info { "Opening channel $path for $username" }
        val receive = Channel<RecordEvent>()

        val channel =
            createChannel(
                path,
                object : ChannelListener {
                  override fun onChannelData(
                      channel: com.caplin.streamlink.Channel,
                      event: RecordType1Event,
                  ) {
                    val event = UpdateEvent(event.fields.toPersistentMap())
                    receive.trySendBlocking(event).onFailure {
                      if (it !is CancellationException)
                          kLogger.error(it) { "Failed to send $event" }
                      if (it != null) throw it
                    }
                  }

                  override fun onChannelStatus(
                      channel: com.caplin.streamlink.Channel,
                      event: SubscriptionStatusEvent,
                  ) {
                    val event =
                        StatusEvent(
                            event.status,
                            event.statusMessage.orEmpty(),
                            event.fields.toPersistentMap(),
                        )
                    receive.trySendBlocking(event).onFailure {
                      if (it !is CancellationException)
                          kLogger.error(it) { "Failed to send $event" }
                      if (it != null) throw it
                    }
                  }

                  override fun onChannelError(
                      channel: com.caplin.streamlink.Channel,
                      event: SubscriptionErrorEvent,
                  ) {
                    val event = ErrorEvent(event.error, event.errorReason)
                    receive.trySendBlocking(event).onFailure {
                      if (it !is CancellationException)
                          kLogger.error(it) { "Failed to send $event" }
                      if (it != null) throw it
                    }
                  }
                },
                null,
            )

        send
            .onEach { channel.send(it, null) }
            .onCompletion {
              channel.closeChannel()
              receive.close()
              kLogger.info { "Channel closed $path for $username" }
            }
            .launchIn(scope)

        return receive.consumeAsFlow().dropWhile { it is UpdateEvent && it.payload.isEmpty() }
      }

      override fun <T, R> getChannel(
          subject: Subject,
          send: Flow<T>,
          type: KClass<R & Any>,
      ): Flow<Event<R>> {
        val path = subject.path
        kLogger.info { "Opening channel $path for $username" }
        val receive = Channel<Event<R>>()

        val channel =
            createJsonChannel(
                path,
                object : JsonChannelListener {

                  override fun onChannelData(channel: JsonChannel, event: JsonEvent) {
                    val event =
                        UpdateEvent(
                            objectMapper.convertValue(
                                event.json as JsonNode,
                                type.java,
                            ),
                        )
                    receive.trySendBlocking(event).onFailure {
                      if (it !is CancellationException)
                          kLogger.error(it) { "Failed to send $event" }
                      if (it != null) throw it
                    }
                  }

                  override fun onChannelStatus(
                      channel: JsonChannel,
                      event: SubscriptionStatusEvent,
                  ) {
                    val event =
                        StatusEvent(
                            event.status,
                            event.statusMessage.orEmpty(),
                            event.fields.toPersistentMap(),
                        )
                    receive.trySendBlocking(event).onFailure {
                      if (it !is CancellationException)
                          kLogger.error(it) { "Failed to send $event" }
                      if (it != null) throw it
                    }
                  }

                  override fun onChannelError(channel: JsonChannel, event: SubscriptionErrorEvent) {
                    val event = ErrorEvent(event.error, event.errorReason)
                    receive.trySendBlocking(event).onFailure {
                      if (it !is CancellationException)
                          kLogger.error(it) { "Failed to send $event" }
                      if (it != null) throw it
                    }
                  }
                },
                null,
            )

        send
            .onEach { event -> channel.send(event, null) }
            .onCompletion {
              channel.closeChannel()
              receive.close()
              kLogger.info { "Channel closed $path for $username" }
            }
            .launchIn(scope)

        return receive.consumeAsFlow()
      }

      override fun getContainer(subject: Subject): Flow<ContainerEvent> =
          getSubject(subject.path) { channel ->
            object : SubscriptionListener by DefaultSubscriptionListener(channel) {

              override fun onContainerUpdate(
                  subscription: Subscription,
                  event: com.caplin.streamlink.ContainerEvent,
              ) {
                var changes = persistentListOf<ContainerChange>()
                event.updateModel(
                    object : ContainerModel {

                      override fun clear() {
                        changes = changes.add(Clear)
                      }

                      override fun insert(index: Int, element: ContainerElement) {
                        changes = changes.add(Added(index, element.subject))
                      }

                      override fun remove(index: Int, element: ContainerElement) {
                        changes = changes.add(Removed(index, element.subject))
                      }

                      override fun move(fromIndex: Int, toIndex: Int, element: ContainerElement) {
                        remove(fromIndex, element)
                        insert(toIndex, element)
                      }
                    },
                )
                changes.forEach { change ->
                  val event = UpdateEvent(change)
                  channel.trySendBlocking(event).onFailure {
                    if (it !is CancellationException) kLogger.error(it) { "Failed to send $event" }
                    if (it != null) throw it
                  }
                }
              }
            }
          }

      override fun getSubject(subject: Subject): Flow<RecordEvent> =
          getSubject(subject.path) { channel ->
            object : SubscriptionListener by DefaultSubscriptionListener(channel) {

              override fun onRecordUpdate(subscription: Subscription, event: RecordType1Event) {
                val event = UpdateEvent(event.fields.toPersistentMap())
                channel.trySendBlocking(event).onFailure {
                  if (it !is CancellationException) kLogger.error(it) { "Failed to send $event" }
                  if (it != null) throw it
                }
              }
            }
          }

      override fun <T> getSubject(subject: Subject, type: KClass<T & Any>) =
          getSubject(subject.path) { channel ->
            object : SubscriptionListener by DefaultSubscriptionListener(channel) {

              override fun onJsonUpdate(subscription: Subscription, event: JsonEvent) {
                val event =
                    UpdateEvent(
                        objectMapper.convertValue(
                            event.json as JsonNode,
                            type.java,
                        ),
                    )
                channel.trySendBlocking(event).onFailure {
                  if (it !is CancellationException) kLogger.error(it) { "Failed to send $event" }
                  if (it != null) throw it
                }
              }
            }
          }

      private fun <T> getSubject(
          path: String,
          block: (SendChannel<Event<T>>) -> SubscriptionListener,
      ): Flow<Event<T>> = callbackFlow {
        kLogger.info { "Subscribing to subject $path for $username" }

        val handle =
            subscribe(
                path,
                block(channel),
            )

        awaitClose {
          handle.unsubscribe()
          kLogger.info { "Unsubscribed from subject $path for $username" }
        }
      }

      override fun connect() {
        check(!connected.load()) { "Create a new connection to reconnect." }
      }

      override fun disconnect() {
        if (connected.exchange(false)) {
          kLogger.info { "Disconnecting as $username" }
          sl.disconnect()
        }
      }

      override fun close() {
        disconnect()
      }
    }
  }
}
