@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.util.flow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.whileSelect

/**
 * Demultiplexes the elements of the flow based on a key selector function. Each element in the flow
 * is assigned a key based on the result of the key selector function. Elements with the same key
 * are grouped together and then processed by the flow producer function.
 *
 * @param keySelector the function that determines the key for each element in the flow. The key can
 *   be of any type.
 * @param flowProducer the function that processes the grouped elements for each key. It takes the
 *   key and a flow of elements with the same key as input.
 * @return a new flow that emits the results of the flow producer function.
 */
fun <T, K, R> Flow<T>.demultiplexBy(
    keySelector: (T) -> K?,
    flowProducer: suspend FlowCollector<R>.(K, Flow<T>) -> Unit,
): Flow<R> = callbackFlow {
  val receiveChannel = produceIn(this)
  val completedKeysChannel = Channel<K>()
  val keyResponseChannels = mutableMapOf<K, Channel<T>>()
  whileSelect {
    receiveChannel.onReceiveCatching { result ->
      result
          .onSuccess { event ->
            keySelector(event)?.let { key ->
              keyResponseChannels
                  .getOrPut(key) {
                    Channel<T>().also { keyResponseChannel ->
                      keyResponseChannels[key] = keyResponseChannel
                      flow { flowProducer(key, keyResponseChannel.consumeAsFlow()) }
                          .onEach { response -> send(response) }
                          .onCompletion { completedKeysChannel.send(key) }
                          .launchIn(this@callbackFlow)
                    }
                  }
                  .send(event)
            }
          }
          .isSuccess
    }
    completedKeysChannel.onReceiveCatching { result ->
      result.onSuccess { key -> keyResponseChannels.remove(key)?.close() }.isSuccess
    }
  }

  completedKeysChannel.cancel()
  receiveChannel.cancel()
  keyResponseChannels.values.forEach { it.cancel() }
}
