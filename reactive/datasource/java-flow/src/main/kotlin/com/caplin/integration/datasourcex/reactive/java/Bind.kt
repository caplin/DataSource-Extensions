package com.caplin.integration.datasourcex.reactive.java

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.reactive.api.BindContext
import com.caplin.integration.datasourcex.reactive.api.BindMarker
import com.caplin.integration.datasourcex.reactive.api.ServiceConfig
import com.caplin.integration.datasourcex.reactive.core.Binder
import com.caplin.integration.datasourcex.reactive.core.IFlowAdapter
import com.caplin.integration.datasourcex.reactive.core.ScopedDataSource
import java.util.concurrent.Flow.Publisher
import java.util.function.Consumer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.FlowAdapters

@BindMarker
class Bind internal constructor(private val binder: Binder) {

  val context: BindContext =
      object : BindContext {
        override val dataSource = binder.dataSource
      }

  companion object {
    @Suppress("UNCHECKED_CAST")
    private object Adapter : IFlowAdapter {
      @Suppress("UNCHECKED_CAST")
      override fun <T> asFlow(p: Any): Flow<T> =
          FlowAdapters.toPublisher(p as Publisher<*>).asFlow() as Flow<T>

      override fun <P> asPublisher(p: Flow<Any>): P =
          FlowAdapters.toFlowPublisher(p.asPublisher()) as P
    }

    /** DSL entrypoint for binding [Publisher] streams to [DataSource] endpoints. */
    @JvmStatic
    fun using(dataSource: DataSource, bind: Consumer<Bind>) {
      using(dataSource) { bind.accept(this) }
    }

    /** DSL entrypoint for binding [Publisher] streams to [DataSource] endpoints. */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmSynthetic
    fun using(dataSource: DataSource, bind: Bind.() -> Unit) {
      Bind(Binder(ScopedDataSource(dataSource, GlobalScope))).also(bind)
    }
  }

  /** Bind to a named dynamic service. */
  fun to(name: String, serviceConfig: Consumer<ServiceConfig> = Consumer {}, bind: Consumer<Bind>) {
    to(name, { serviceConfig.accept(this) }, { bind.accept(this) })
  }

  /** Bind to a named dynamic service. */
  @JvmSynthetic
  fun to(name: String, serviceConfig: ServiceConfig.() -> Unit = {}, bind: Bind.() -> Unit) {
    binder.withServiceConfig(ServiceConfig(name).also(serviceConfig::invoke)) {
      Bind(it).also(bind::invoke)
    }
  }

  /**
   * Configure bindings for channels.
   *
   * This should be used for bidirectional communication. A channel will be opened when first
   * requested and closed when no longer needed.
   */
  fun channel(channel: Consumer<BindChannel>) {
    channel { channel.accept(this) }
  }

  /**
   * Configure bindings for channels.
   *
   * This should be used for bidirectional communication. A channel will be opened when first
   * requested and closed when no longer needed.
   */
  @JvmSynthetic
  fun channel(channel: BindChannel.() -> Unit) {
    BindChannel(binder, Adapter).also(channel::invoke)
  }

  /**
   * Configure bindings for Active (AKA Cold) publishers.
   *
   * This should be used for demand driven publication. Publication will be started when first
   * requested and cancelled when no longer needed.
   */
  fun active(active: Consumer<BindActive>) {
    active { active.accept(this) }
  }

  /**
   * Configure bindings for Active (AKA Cold) publishers.
   *
   * This should be used for demand driven publication. Publication will be started when first
   * requested and cancelled when no longer needed.
   */
  @JvmSynthetic
  fun active(active: BindActive.() -> Unit) {
    BindActive(binder, Adapter).also(active::invoke)
  }

  /**
   * Configure bindings for Active (AKA Cold) container publishers.
   *
   * This should be used for demand-driven container publication. Publication will be started when
   * first requested and cancelled when no longer needed.
   */
  fun activeContainer(activeContainer: Consumer<BindActiveContainer>) {
    activeContainer { activeContainer.accept(this) }
  }

  /**
   * Configure bindings for Active (AKA Cold) container publishers.
   *
   * This should be used for demand-driven container publication. Publication will be started when
   * first requested and cancelled when no longer needed.
   */
  @JvmSynthetic
  fun activeContainer(activeContainer: BindActiveContainer.() -> Unit) {
    BindActiveContainer(binder, Adapter).also(activeContainer::invoke)
  }

  /**
   * Configure bindings for Broadcast (AKA Hot) publishers.
   *
   * This should be used for publishing data regardless of demand. Publication will be started
   * eagerly and never cancelled.
   */
  fun broadcast(broadcast: Consumer<BindBroadcast>) {
    broadcast { broadcast.accept(this) }
  }

  /**
   * Configure bindings for Broadcast (AKA Hot) publishers.
   *
   * This should be used for publishing data regardless of demand. Publication will be started
   * eagerly and never cancelled.
   */
  @JvmSynthetic
  fun broadcast(broadcast: BindBroadcast.() -> Unit) {
    BindBroadcast(binder, Adapter).also(broadcast::invoke)
  }
}
