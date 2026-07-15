package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.Service
import com.caplin.datasource.internal.ServiceImpl
import com.caplin.datasource.publisher.CachingPublisher
import com.caplin.integration.datasourcex.reactive.api.ServiceConfig
import com.caplin.integration.datasourcex.util.AntPatternNamespace
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
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
    })
