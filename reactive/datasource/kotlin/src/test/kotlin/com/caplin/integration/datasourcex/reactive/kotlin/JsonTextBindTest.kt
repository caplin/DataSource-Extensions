package com.caplin.integration.datasourcex.reactive.kotlin

import com.caplin.datasource.DataSource
import com.caplin.datasource.channel.JsonChannel
import com.caplin.datasource.channel.JsonChannelListener
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.json.JsonChannelMessage
import com.caplin.datasource.messaging.json.JsonHandler
import com.caplin.datasource.messaging.json.JsonMessage
import com.caplin.datasource.publisher.CachingDataProvider
import com.caplin.datasource.publisher.CachingPublisher
import com.caplin.integration.datasourcex.reactive.api.JsonText
import com.caplin.integration.datasourcex.reactive.api.JsonTree
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

private data class HandlerTree(val json: String)

private class ForeignTree(val json: String) {
  override fun toString() = json
}

class JsonTextBindTest :
    FunSpec({
      isolationMode = IsolationMode.InstancePerTest

      // Stands in for whichever JSON library the DataSource's handler uses.
      val jsonHandler =
          mockk<JsonHandler<Any?>> {
            every { parse(any()) } answers { HandlerTree(firstArg()) }
            every { format(any()) } answers { firstArg<HandlerTree>().json }
          }

      val cachedMessageFactory =
          mockk<CachedMessageFactory> {
            every { createJsonMessage(any(), any()) } returns mockk<JsonMessage>()
          }

      val cachingPublisher =
          mockk<CachingPublisher> {
            every { this@mockk.cachedMessageFactory } returns cachedMessageFactory
            every { publish(any()) } just Runs
          }

      val dataProvider = slot<CachingDataProvider>()
      val channelListener = slot<JsonChannelListener>()

      val dataSource =
          mockk<DataSource> {
            every { extraConfiguration.jsonHandler } returns jsonHandler
            every {
              createCachingPublisher(any<AntPatternNamespace>(), capture(dataProvider))
            } answers
                {
                  secondArg<CachingDataProvider>().setPublisher(cachingPublisher)
                  cachingPublisher
                }
            every { addJsonChannelListener(any(), capture(channelListener)) } just Runs
          }

      test("An active subject's JsonText is published as the handler's tree") {
        val updates = MutableSharedFlow<Any>()
        dataSource.bind(scope = backgroundScope) {
          active { json { pattern(pattern = "/SUBJECT/**") { updates } } }
        }

        dataProvider.captured.onRequest("/SUBJECT/1")
        delay(1.milliseconds)
        updates.emit(JsonText("""{"a":1}"""))

        verify { cachedMessageFactory.createJsonMessage("/SUBJECT/1", HandlerTree("""{"a":1}""")) }
      }

      test("A JsonText channel receives each payload as text and sends JsonText as a tree") {
        dataSource.bind(scope = backgroundScope) {
          channel {
            json {
              namespace(AntPatternNamespace("/CHANNEL/{id}"), JsonText::class.java) {
                receive.map { JsonText(it.json.uppercase()) }
              }
            }
          }
        }

        val channel =
            mockk<JsonChannel> {
              every { subject } returns "/CHANNEL/1"
              every { send(any()) } just Runs
            }
        val message =
            mockk<JsonChannelMessage> { every { jsonObject } returns HandlerTree("""{"b":2}""") }

        channelListener.captured.onChannelOpen(channel)
        channelListener.captured.onMessageReceived(channel, message)
        delay(1.milliseconds)

        verify { channel.send(HandlerTree("""{"B":2}""")) }
      }
      test("A JsonTree of the handler's library is published as it stands") {
        val updates = MutableSharedFlow<Any>()
        dataSource.bind(scope = backgroundScope) {
          active { json { pattern(pattern = "/SUBJECT/**") { updates } } }
        }

        dataProvider.captured.onRequest("/SUBJECT/1")
        delay(1.milliseconds)
        val tree = HandlerTree("""{"a":1}""")
        updates.emit(JsonTree(tree))

        verify { cachedMessageFactory.createJsonMessage("/SUBJECT/1", refEq(tree)) }
        verify(exactly = 0) { jsonHandler.parse("""{"a":1}""") }
      }

      test("A JsonTree of another library is converted through its JSON text") {
        val updates = MutableSharedFlow<Any>()
        dataSource.bind(scope = backgroundScope) {
          active { json { pattern(pattern = "/SUBJECT/**") { updates } } }
        }

        dataProvider.captured.onRequest("/SUBJECT/1")
        delay(1.milliseconds)
        updates.emit(JsonTree(ForeignTree("""{"a":1}""")))

        verify { cachedMessageFactory.createJsonMessage("/SUBJECT/1", HandlerTree("""{"a":1}""")) }
      }

      test("A JsonTree channel receives each payload as the handler's tree") {
        dataSource.bind(scope = backgroundScope) {
          channel {
            json {
              namespace(AntPatternNamespace("/CHANNEL/{id}"), JsonTree::class.java) {
                receive.map { JsonTree(HandlerTree((it.tree as HandlerTree).json.uppercase())) }
              }
            }
          }
        }

        val channel =
            mockk<JsonChannel> {
              every { subject } returns "/CHANNEL/1"
              every { send(any()) } just Runs
            }
        val message =
            mockk<JsonChannelMessage> { every { jsonObject } returns HandlerTree("""{"b":2}""") }

        channelListener.captured.onChannelOpen(channel)
        channelListener.captured.onMessageReceived(channel, message)
        delay(1.milliseconds)

        verify { channel.send(HandlerTree("""{"B":2}""")) }
      }
    })
