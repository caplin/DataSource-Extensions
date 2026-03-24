@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect

/**
 * Throttles the emission of values so that they are at least [timeMillis] apart. Drops the older
 * value(s) if two or more values are emitted within the time period. The first element is emitted
 * immediately.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Flow<T>.throttleLatest(timeMillis: Long): Flow<T> = flow {
  coroutineScope {
    val receiveChannel = produceIn(this)

    emit(receiveChannel.receiveCatching().getOrNull() ?: return@coroutineScope)

    var delayJob: Job? = launch { delay(timeMillis) }

    var queuedEvent: Any? = UNSET
    whileSelect {
      receiveChannel.onReceiveCatching { channelResult ->
        channelResult
            .onSuccess { event ->
              if (delayJob == null) {
                emit(event)
                delayJob = launch { delay(timeMillis) }
              } else queuedEvent = event
            }
            .onFailure {
              if (queuedEvent !== UNSET) {
                emit(queuedEvent as T)
              }
            }
            .isSuccess
      }
      delayJob?.onJoin?.invoke {
        delayJob =
            if (queuedEvent === UNSET) null
            else {
              emit(queuedEvent as T)
              queuedEvent = UNSET
              launch { delay(timeMillis) }
            }
        true
      }
    }
  }
}
