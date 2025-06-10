@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.whileSelect

/**
 * Buffers all elements emitted until there is a period of no emissions greater than
 * [timeoutMillis], then emits all buffered elements within a [List].
 *
 * If the upstream [Flow] completes, any remaining elements are emitted immediately.
 */
fun <T : Any?> Flow<T>.bufferingDebounce(timeoutMillis: Long): Flow<List<T>> = channelFlow {
  val itemChannel = produceIn(this)
  var bufferedItems = mutableListOf<T>()
  whileSelect {
    if (bufferedItems.isNotEmpty())
        onTimeout(timeoutMillis) {
          send(bufferedItems)
          bufferedItems = mutableListOf()
          true
        }
    itemChannel.onReceiveCatching { result ->
      result
          .onSuccess { item -> bufferedItems += item }
          .onFailure { if (bufferedItems.isNotEmpty()) send(bufferedItems) }
          .isSuccess
    }
  }
}

/**
 * Buffers all elements emitted until there is a period of no emissions greater than [timeout], then
 * emits all buffered elements within a [List].
 *
 * If the upstream [Flow] completes, any remaining elements are emitted immediately.
 */
fun <T : Any?> Flow<T>.bufferingDebounce(timeout: Duration): Flow<List<T>> =
    bufferingDebounce(timeout.toMillis())
