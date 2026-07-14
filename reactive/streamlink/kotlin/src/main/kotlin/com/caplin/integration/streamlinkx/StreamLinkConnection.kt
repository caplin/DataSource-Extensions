package com.caplin.integration.streamlinkx

import com.caplin.integration.datasourcex.util.Subject
import com.caplin.streamlink.ConnectionState
import com.caplin.streamlink.ConnectionStatusEvent
import com.caplin.streamlink.ServiceStatus
import com.caplin.streamlink.StreamLink
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/**
 * A coroutine- and [Flow]-friendly wrapper around a Caplin [StreamLink] connection.
 *
 * Extends the raw [StreamLink] API with suspending helpers for awaiting connection and service
 * state, and with `getSubject`/`getChannel` overloads that expose subscriptions and channels as
 * cold [Flow]s of [Event]s. Instances are created via [StreamLinkConnectionFactory.connect].
 *
 * Implements [AutoCloseable]; closing disconnects the underlying StreamLink.
 */
interface StreamLinkConnection : StreamLink, AutoCloseable {

  companion object {
    private val SERVICE_UP_POLL_INTERVAL = 100.milliseconds

    /**
     * Opens a bidirectional channel on [subject], sending items from [send] and receiving updates
     * deserialised to [R]. Reified convenience for [getChannel] taking an explicit [KClass].
     */
    inline fun <T, reified R : Any> StreamLinkConnection.getChannel(
        subject: Subject,
        send: Flow<T>,
    ): Flow<Event<R>> = getChannel(subject, send, R::class)

    /**
     * Subscribes to [subject], deserialising each JSON update to [T]. Reified convenience for
     * [getSubject] taking an explicit [KClass].
     */
    inline fun <reified T : Any> StreamLinkConnection.getSubject(subject: Subject): Flow<Event<T>> =
        getSubject(subject, T::class)
  }

  /** The username this connection is authenticated as. */
  val username: String

  /** A hot [SharedFlow] of connection status changes, replaying the latest event to collectors. */
  val state: SharedFlow<ConnectionStatusEvent>

  /** Suspends until the connection reaches the [ConnectionState.LOGGEDIN] state. */
  suspend fun awaitConnected() {
    state.first { it.connectionState == ConnectionState.LOGGEDIN }
  }

  /** Suspends until the service named [name] reports [ServiceStatus.OK], polling periodically. */
  suspend fun awaitServiceUp(name: String) {
    while (true) {
      if (
          connectionCurrentState.serviceStatusArray
              .firstOrNull { it.serviceName == name }
              ?.serviceStatus == ServiceStatus.OK
      )
          return
      delay(SERVICE_UP_POLL_INTERVAL)
    }
  }

  /**
   * Subscribes to the container at [subject], emitting a [ContainerEvent] for each row add, remove
   * or clear as its membership changes.
   */
  fun getContainer(subject: Subject): Flow<ContainerEvent>

  /**
   * Opens a record channel on [subject], sending each emitted field map from [send] to the server
   * and emitting the server's record updates as [RecordEvent]s.
   */
  fun getChannel(subject: Subject, send: Flow<Map<String, String>>): Flow<RecordEvent>

  /**
   * Opens a JSON channel on [subject], sending each item from [send] to the server and emitting the
   * server's updates deserialised to [type].
   */
  fun <T, R> getChannel(subject: Subject, send: Flow<T>, type: KClass<R & Any>): Flow<Event<R>>

  /** Subscribes to the record at [subject], emitting a [RecordEvent] for each field update. */
  fun getSubject(subject: Subject): Flow<RecordEvent>

  /** Subscribes to the JSON record at [subject], deserialising each update to [type]. */
  fun <T> getSubject(subject: Subject, type: KClass<T & Any>): Flow<Event<T>>
}
