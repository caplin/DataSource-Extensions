package com.caplin.integration.datasourcex.reactive.core

import com.caplin.integration.datasourcex.reactive.api.ActiveContainerConfig
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent
import com.caplin.integration.datasourcex.util.flow.bufferingDebounce
import com.caplin.integration.datasourcex.util.getLogger
import java.net.URLEncoder
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.time.withTimeoutOrNull

internal class Container<T : Any>(
    private val containerSubject: String,
    private val config: ActiveContainerConfig,
    private val supplier: (String) -> Flow<ContainerEvent<T>>,
    internal val scope: CoroutineScope,
) {
  private companion object {
    val logger = getLogger<Container<*>>()
  }

  private sealed interface ContainerState<out T> {
    data object Initialising : ContainerState<Nothing>

    @JvmInline
    value class Content<T>(val records: Map<String, MutableStateFlow<T>>) : ContainerState<T>

    @JvmInline value class Completed(val throwable: Throwable? = null) : ContainerState<Nothing>
  }

  private val containerState: Flow<ContainerState<T>> =
      flow {
            logger.info { "Creating container $containerSubject" }
            emit(ContainerState.Initialising)
            var rows = persistentMapOf<String, MutableStateFlow<T>>()

            supplier(containerSubject)
                .onStart { emit(ContainerEvent.Bulk(emptyList())) }
                .bufferingDebounce(config.structureDebounce)
                .collect { containerEvents ->
                  rows =
                      rows.mutate { mutableRows ->
                        fun handleEvent(event: ContainerEvent.RowEvent<T>) {
                          val rowSubject =
                              "$containerSubject${config.rowPathSuffix}/${URLEncoder.encode(event.key, Charsets.UTF_8)}"
                          when (event) {
                            is ContainerEvent.RowEvent.Remove -> mutableRows.remove(rowSubject)
                            is ContainerEvent.RowEvent.Upsert ->
                                mutableRows[rowSubject]?.let { it.value = event.value }
                                    ?: run {
                                      mutableRows[rowSubject] = MutableStateFlow(event.value)
                                    }
                          }
                        }

                        containerEvents.forEach { event ->
                          when (event) {
                            is ContainerEvent.Bulk -> event.events.forEach(::handleEvent)
                            is ContainerEvent.RowEvent<T> -> handleEvent(event)
                          }
                        }
                      }

                  emit(ContainerState.Content(rows))
                }
            emit(ContainerState.Content(emptyMap()))
          }
          .distinctUntilChanged()
          .onCompletion {
            if (it == null) {
              logger.info { "Completing container $containerSubject" }
              emit(ContainerState.Completed())
            } else if (it is CancellationException) {
              logger.info { "Cancelling container $containerSubject" }
            }
          }
          .catch { e ->
            logger.warn(e) { "Unhandled exception in container $containerSubject" }
            emit(ContainerState.Completed(e))
          }
          .shareIn(
              scope = scope, started = SharingStarted.Companion.WhileSubscribed(0, 0), replay = 1)
          .takeWhile { it !is ContainerState.Completed }

  val containerEventsFlow: Flow<List<InternalContainerEvent>> = flow {
    var previousRows: Set<String>?
    var currentRows: Set<String>? = null
    containerState
        .filterIsInstance<ContainerState.Content<T>>()
        .map { it.records.keys }
        .collect { rows ->
          previousRows = currentRows
          currentRows = rows
          emit(
              (currentRows.orEmpty() - previousRows.orEmpty()).map(
                  InternalContainerEvent::Inserted) +
                  (previousRows.orEmpty() - currentRows.orEmpty()).map(
                      InternalContainerEvent::Removed))
        }
  }

  fun getRowFlow(rowSubject: String): Flow<T> = flow {
    val rowFlow =
        withTimeoutOrNull(config.rowRequestTimeout) {
          containerState
              .filterIsInstance<ContainerState.Content<T>>()
              .map { content -> content.records[rowSubject] }
              .filterNotNull()
              .first()
        }
    rowFlow?.let { emitAll(it) }
  }
}
