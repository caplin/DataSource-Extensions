package com.caplin.integration.datasourcex.util.flow

import kotlinx.coroutines.flow.Flow

/**
 * Casts the elements of the current [Flow] to the specified type [R]. The elements that cannot be
 * cast to [R] will cause a [ClassCastException].
 *
 * @return A new [Flow] containing the cast elements of type [R].
 */
@Suppress("UNCHECKED_CAST") fun <R : Any?> Flow<*>.cast(): Flow<R> = this as Flow<R>
