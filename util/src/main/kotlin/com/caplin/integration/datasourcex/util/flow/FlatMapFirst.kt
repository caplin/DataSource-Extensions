package com.caplin.integration.datasourcex.util.flow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch

/**
 * Applies the given suspend lambda function to the first emitted value of the original flow and the
 * entire original flow, producing a new flow of type R.
 *
 * @param block The suspend lambda function to be applied to the first emitted value of the original
 *   flow and the original flow. The lambda function takes two parameters: the first emitted value
 *   of the original flow and the original flow itself. The lambda function should return a flow of
 *   type R.
 * @return A new flow of type R emitted by the given lambda function.
 */
fun <T, R> Flow<T>.flatMapFirst(block: suspend (first: T, upstream: Flow<T>) -> Flow<R>): Flow<R> =
    channelFlow {
      val receiveChannel = produceIn(this)
      receiveChannel
          .receiveCatching()
          .onFailure { if (it != null) throw it }
          .onSuccess { first ->
            val sendChannel: Channel<T> = Channel(BUFFERED)
            sendChannel.send(first)
            launch {
              receiveChannel.consumeEach { sendChannel.send(it) }
              sendChannel.close()
            }

            block(first, sendChannel.consumeAsFlow()).collect { send(it) }
          }
    }

/**
 * Subscribes to the upstream and when it receives the first element calls [block] and then emits
 * the returned [Flow]
 */
fun <T, R> Flow<T>.flatMapFirst(block: suspend (first: T) -> Flow<R>): Flow<R> = flow {
  emitAll(block(first()))
}
