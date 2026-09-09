package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.Service
import com.caplin.datasource.internal.ServiceImpl
import com.caplin.datasource.publisher.CachingDataProvider
import com.caplin.datasource.publisher.CachingPublisher
import com.caplin.integration.datasourcex.reactive.api.ServiceConfig
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf

class BinderTest :
    FunSpec({
      test("Ant namespace binding to a service") {
        mockkConstructor(ServiceImpl::class)

        val publisher = mockk<CachingPublisher>()
        val mockDataSource =
            mockk<ScopedDataSource> {
              every { configuration } returns
                  mockk { every { getStringValue("datasrc-local-label") } returns "local-label" }
              every { createService(any()) } just Runs
              every { createCachingPublisher(any(), any()) } returns publisher
            }
        val binder = Binder(mockDataSource)

        val namespace = AntPatternNamespace("/PRIVATE/{username}/{param}")
        binder.withServiceConfig(ServiceConfig("abc")) {
          it.bindActiveRecord({}, namespace, { flowOf(mapOf()) })
        }

        val service = slot<Service>()
        verify { mockDataSource.createService(capture(service)) }

        verify {
          (service.captured as ServiceImpl).addIncludePattern("^\\/PRIVATE\\/[^/]*\\/[^/]*$")
        }
      }

      test("A subscription that completes immediately is removed from the subscription map") {
        val publisher = mockk<CachingPublisher>(relaxed = true)
        val provider = slot<CachingDataProvider>()
        val mockDataSource =
            mockk<ScopedDataSource> {
              every { coroutineContext } returns Dispatchers.Unconfined
              every { createCachingPublisher(any(), capture(provider)) } returns publisher
            }

        Binder(mockDataSource).bindActiveRecord({}, AntPatternNamespace("/prices/{id}")) {
          flowOf(mapOf())
        }
        provider.captured.setPublisher(publisher)

        // The supplier completes before onRequest returns, so the completion handler must find the
        // entry already in the map. Otherwise a dead job is left behind and the subject is
        // permanently unrequestable.
        provider.captured.onRequest("/prices/1")

        shouldNotThrowAny { provider.captured.onRequest("/prices/1") }
      }
    })
