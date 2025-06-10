package com.caplin.integration.datasourcex.spring.annotations

import com.caplin.integration.datasourcex.reactive.api.RecordType
import com.caplin.integration.datasourcex.reactive.api.RecordType.GENERIC
import org.springframework.core.annotation.AliasFor
import org.springframework.messaging.handler.annotation.MessageMapping

/**
 * Methods annotated with this message should return a Map, or a stream of Maps.
 *
 * Updates will be published as the record type determined by [recordType].
 *
 * [toString] will be used to convert the contents to String key value pairs.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@MessageMapping
annotation class RecordMessageMapping(
    @get:AliasFor(annotation = MessageMapping::class) vararg val value: String,
    val recordType: RecordType = GENERIC,
)
