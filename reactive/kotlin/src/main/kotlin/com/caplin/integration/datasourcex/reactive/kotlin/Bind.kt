package com.caplin.integration.datasourcex.reactive.kotlin

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.reactive.api.BindMarker
import com.caplin.integration.datasourcex.reactive.api.ServiceConfig
import com.caplin.integration.datasourcex.reactive.core.Binder
import com.caplin.integration.datasourcex.reactive.core.IFlowAdapter
import com.caplin.integration.datasourcex.reactive.core.ScopedDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow

/** DSL entrypoint for binding [Flow] publishers to [DataSource] endpoints. */
@OptIn(DelicateCoroutinesApi::class)
fun DataSource.bind(bind: Bind.() -> Unit) {
  bind(GlobalScope, bind)
}

internal fun DataSource.bind(scope: CoroutineScope, bind: Bind.() -> Unit) {
  Bind(Binder(ScopedDataSource(this, scope))).also(bind)
}

@BindMarker
class Bind internal constructor(private val binder: Binder) {

  private companion object {
    @Suppress("UNCHECKED_CAST")
    private object Adapter : IFlowAdapter {

      override fun <T> asFlow(p: Any): Flow<T> = p as Flow<T>

      override fun <P> asPublisher(p: Flow<Any>): P = p as P
    }
  }

  /** Bind to a named dynamic service. */
  fun to(name: String, serviceConfig: ServiceConfig.() -> Unit = {}, bind: Bind.() -> Unit) {
    binder.withServiceConfig(ServiceConfig(name).also(serviceConfig)) { Bind(it).bind() }
  }

  /**
   * Configure bindings for channels.
   *
   * This should be used for bidirectional communication. A channel will be opened when first
   * requested and closed when no longer needed.
   */
  fun channel(channel: BindChannel.() -> Unit) {
    BindChannel(binder, Adapter).also(channel)
  }

  /**
   * Configure bindings for Active (AKA Cold) publishers.
   *
   * This should be used for demand-driven publication. Publication will be started when first
   * requested and cancelled when no longer needed.
   */
  fun active(active: BindActive.() -> Unit) {
    BindActive(binder, Adapter).also(active)
  }

  /**
   * Configure bindings for Active (AKA Cold) container publishers.
   *
   * This should be used for demand-driven container publication. Publication will be started when
   * first requested and cancelled when no longer needed.
   */
  fun activeContainer(activeContainer: BindActiveContainer.() -> Unit) {
    BindActiveContainer(binder, Adapter).also(activeContainer)
  }

  /**
   * Configure bindings for Broadcast (AKA Hot) publishers.
   *
   * This should be used for publishing data regardless of demand. Publication will be started
   * eagerly and never cancelled.
   */
  fun broadcast(broadcast: BindBroadcast.() -> Unit) {
    BindBroadcast(binder, Adapter).also(broadcast)
  }
}
