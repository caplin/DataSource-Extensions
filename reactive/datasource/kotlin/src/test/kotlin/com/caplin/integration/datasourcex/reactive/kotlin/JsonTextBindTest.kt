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

class JsonTextBindTest :
    FunSpec({
      isolationMode = IsolationMode.InstancePerTest

      // Stands in for whichever Jackson the DataSource holds: trees are tagged strings.
      val jsonHandler =
          mockk<JsonHandler<Any?>> {
            every { parse(any()) } answers { "tree:" + firstArg<String>() }
            every { format(any()) } answers { firstArg<String>().removePrefix("tree:") }
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

        verify { cachedMessageFactory.createJsonMessage("/SUBJECT/1", """tree:{"a":1}""") }
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
        val message = mockk<JsonChannelMessage> { every { jsonObject } returns """tree:{"b":2}""" }

        channelListener.captured.onChannelOpen(channel)
        channelListener.captured.onMessageReceived(channel, message)
        delay(1.milliseconds)

        verify { channel.send("""tree:{"B":2}""") }
      }
    })
