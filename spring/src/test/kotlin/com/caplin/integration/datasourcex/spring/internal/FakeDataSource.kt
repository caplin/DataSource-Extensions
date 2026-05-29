package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.publisher.CachingDataProvider
import com.caplin.datasource.publisher.CachingPublisher
import io.mockk.every
import io.mockk.mockk

/**
 * A minimal in-memory fake of the Caplin [DataSource] publish path for tests, backed by a relaxed
 * MockK so only the handful of methods the bind/publish path actually uses need behaviour.
 *
 * It captures the [CachingDataProvider] registered for each bound namespace and records every JSON
 * payload handed to the SDK for publishing, so a test can simulate a peer [request] and assert what
 * was published — without a real DataSource, sockets, or a Liberator.
 */
internal class FakeDataSource {

  private val providers = mutableListOf<CachingDataProvider>()

  /** Each `subject to payload` handed to the SDK for JSON publishing, in order. */
  val publishedJson = mutableListOf<Pair<String, Any>>()

  private val cachedMessageFactory =
      mockk<CachedMessageFactory>(relaxed = true) {
        every { createJsonMessage(any(), any()) } answers
            {
              publishedJson += firstArg<String>() to secondArg<Any>()
              mockk(relaxed = true)
            }
      }

  private val cachingPublisher =
      mockk<CachingPublisher>(relaxed = true) {
        every { cachedMessageFactory } returns this@FakeDataSource.cachedMessageFactory
      }

  val dataSource: DataSource =
      mockk(relaxed = true) {
        every { createCachingPublisher(any(), any()) } answers
            {
              val provider = secondArg<CachingDataProvider>()
              provider.setPublisher(cachingPublisher)
              providers += provider
              cachingPublisher
            }
      }

  /** Simulates a peer requesting [subject] from the single bound provider. */
  fun request(subject: String) = providers.single().onRequest(subject)
}
