package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import com.caplin.datasource.channel.JsonChannel
import com.caplin.datasource.channel.JsonChannelListener
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.MessageFactory
import com.caplin.datasource.messaging.container.ContainerMessage
import com.caplin.datasource.messaging.json.JsonChannelMessage
import com.caplin.datasource.messaging.record.GenericMessage
import com.caplin.datasource.publisher.ActivePublisher
import com.caplin.datasource.publisher.CachingDataProvider
import com.caplin.datasource.publisher.CachingPublisher
import com.caplin.datasource.publisher.DataProvider
import com.caplin.datasource.publisher.RequestEvent
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import io.mockk.every
import io.mockk.mockk

/**
 * A minimal in-memory fake of the Caplin [DataSource] publish/channel path for tests, backed by a
 * relaxed MockK so only the methods the bind path uses need behaviour.
 *
 * It captures everything bound (active caching providers, active mapping providers, and channel
 * listeners — each keyed by namespace) and records everything published, so a test can simulate a
 * peer [request] for an active subject or [openChannel]/[sendToChannel] for a channel, and assert
 * what came back — without a real DataSource, sockets, or a Liberator.
 */
class FakeDataSource {

  private val cachingProviders = mutableListOf<Pair<AntPatternNamespace, CachingDataProvider>>()
  private val mappingProviders = mutableListOf<Pair<AntPatternNamespace, DataProvider>>()
  private val channelListeners = mutableListOf<Pair<AntPatternNamespace, JsonChannelListener>>()

  private val channelSends = mutableMapOf<String, MutableList<Any>>()
  private val channels = mutableMapOf<String, JsonChannel>()

  /** Each `subject to payload` published as a JSON message, in order. */
  val publishedJson = mutableListOf<Pair<String, Any>>()
  /** Each `subject to fields` published as a generic record, in order. */
  val publishedRecords = mutableListOf<Pair<String, Map<String, String>>>()
  /** Each `subject to value` published as a mapping message, in order. */
  val publishedMappings = mutableListOf<Pair<String, String>>()
  /** Each `subject to element-subjects` published as a container message, in order. */
  val publishedContainers = mutableListOf<Pair<String, List<String>>>()
  /** Subjects a `SubjectError` (e.g. NotFound) was published for. */
  val erroredSubjects = mutableListOf<String>()

  private val cachedMessageFactory =
      mockk<CachedMessageFactory>(relaxed = true) {
        every { createJsonMessage(any(), any()) } answers
            {
              publishedJson += firstArg<String>() to secondArg<Any>()
              mockk(relaxed = true)
            }
        every { createGenericMessage(any()) } answers { genericMessage(firstArg()) }
        every { createContainerMessage(any()) } answers { containerMessage(firstArg()) }
        every { createSubjectErrorEvent(any(), any(), *anyVararg()) } answers
            {
              erroredSubjects += firstArg<String>()
              mockk(relaxed = true)
            }
      }

  private val messageFactory =
      mockk<MessageFactory>(relaxed = true) {
        every { createMappingMessage(any(), any()) } answers
            {
              publishedMappings += firstArg<String>() to secondArg<String>()
              mockk(relaxed = true)
            }
        every { createSubjectErrorEvent(any(), any(), *anyVararg()) } answers
            {
              erroredSubjects += firstArg<String>()
              mockk(relaxed = true)
            }
      }

  private val cachingPublisher =
      mockk<CachingPublisher>(relaxed = true) {
        every { cachedMessageFactory } returns this@FakeDataSource.cachedMessageFactory
      }

  private val activePublisher =
      mockk<ActivePublisher>(relaxed = true) {
        every { messageFactory } returns this@FakeDataSource.messageFactory
      }

  val dataSource: DataSource =
      mockk(relaxed = true) {
        every { createCachingPublisher(any(), any()) } answers
            {
              val provider = secondArg<CachingDataProvider>()
              provider.setPublisher(cachingPublisher)
              cachingProviders += firstArg<AntPatternNamespace>() to provider
              cachingPublisher
            }
        every { createActivePublisher(any(), any()) } answers
            {
              val provider = secondArg<DataProvider>()
              provider.setPublisher(activePublisher)
              mappingProviders += firstArg<AntPatternNamespace>() to provider
              activePublisher
            }
        every { addJsonChannelListener(any(), any()) } answers
            {
              channelListeners +=
                  firstArg<AntPatternNamespace>() to secondArg<JsonChannelListener>()
            }
      }

  /** Simulates a peer requesting the active [subject] from the matching bound provider. */
  fun request(subject: String) {
    cachingProviders
        .firstOrNull { (namespace, _) -> namespace.match(subject) }
        ?.let { (_, provider) ->
          provider.onRequest(subject)
          return
        }
    mappingProviders
        .firstOrNull { (namespace, _) -> namespace.match(subject) }
        ?.let { (_, provider) ->
          provider.onRequest(requestEvent(subject))
          return
        }
    error("No active provider bound for subject $subject")
  }

  /** Opens a channel for [subject], returning the list that captures values sent to the client. */
  fun openChannel(subject: String): List<Any> {
    listenerFor(subject).onChannelOpen(channelFor(subject))
    return channelSends.getValue(subject)
  }

  /** Sends [request] to the channel for [subject] as if from a connected client. */
  fun sendToChannel(subject: String, request: Any) {
    val message =
        mockk<JsonChannelMessage>(relaxed = true) {
          every { getJsonAsType<Any>(any()) } returns request
        }
    listenerFor(subject).onMessageReceived(channelFor(subject), message)
  }

  private fun listenerFor(subject: String): JsonChannelListener =
      channelListeners.first { (namespace, _) -> namespace.match(subject) }.second

  private fun genericMessage(subject: String): GenericMessage {
    val fields = linkedMapOf<String, String>()
    publishedRecords += subject to fields
    val message = mockk<GenericMessage>(relaxed = true)
    every { message.setField(any(), any()) } answers
        {
          fields[firstArg()] = secondArg()
          message
        }
    return message
  }

  private fun containerMessage(subject: String): ContainerMessage {
    val elements = mutableListOf<String>()
    publishedContainers += subject to elements
    val message = mockk<ContainerMessage>(relaxed = true)
    every { message.addElement(any()) } answers { elements += firstArg<String>() }
    every { message.insertElement(any(), any()) } answers { elements += firstArg<String>() }
    return message
  }

  private fun channelFor(channelSubject: String): JsonChannel =
      channels.getOrPut(channelSubject) {
        val sends = channelSends.getOrPut(channelSubject) { mutableListOf() }
        mockk(relaxed = true) {
          every { subject } returns channelSubject
          every { send(any()) } answers { sends += firstArg<Any>() }
        }
      }

  private fun requestEvent(subj: String): RequestEvent =
      mockk(relaxed = true) { every { subject } returns subj }
}
