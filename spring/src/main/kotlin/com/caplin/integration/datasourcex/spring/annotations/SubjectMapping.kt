package com.caplin.integration.datasourcex.spring.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.springframework.core.annotation.AliasFor
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload

/**
 * Methods annotated with this message should return a String, or a stream of Strings. Liberator
 * will then re-request on the returned subject. If a stream of strings is returned, then the
 * remapping can be updated by emitting a second string, and Liberator will re-request on the new
 * subject.
 *
 * This does not work with methods with a [Payload] or unannotated parameter.
 * > Note that the returned subject will not pass through `object-map` or authentication module
 * > mappings before being requested.
 */
@Target(FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(RUNTIME)
@MustBeDocumented
@MessageMapping
annotation class SubjectMapping(
    @get:AliasFor(annotation = MessageMapping::class) vararg val value: String
)
