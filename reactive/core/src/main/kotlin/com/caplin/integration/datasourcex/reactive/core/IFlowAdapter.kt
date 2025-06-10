package com.caplin.integration.datasourcex.reactive.core

import kotlinx.coroutines.flow.Flow

interface IFlowAdapter {

  fun <T> asFlow(p: Any): Flow<T>

  fun <P> asPublisher(p: Flow<Any>): P
}
