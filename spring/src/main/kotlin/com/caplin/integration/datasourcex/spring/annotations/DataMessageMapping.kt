package com.caplin.integration.datasourcex.spring.annotations

import com.caplin.datasource.DataSource
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.springframework.core.annotation.AliasFor
import org.springframework.messaging.handler.annotation.MessageMapping

@Target(FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(RUNTIME)
@MustBeDocumented
@MessageMapping
annotation class DataMessageMapping(
    @get:AliasFor(annotation = MessageMapping::class) vararg val value: String,
    val type: Type = Type.JSON,
) {

  enum class Type {
    /**
     * Methods annotated with this message should return one or more objects to be serialized to
     * JSON.
     *
     * The JSON handler installed in [DataSource] will be used. By default, this is the
     * [com.fasterxml.jackson.databind.ObjectMapper] provided by Spring Boot.
     */
    JSON,

    /**
     * Methods annotated with this message should return one or more of [Map].
     *
     * [toString] will be used to convert the keys and values of the map to String key value pairs.
     */
    RECORD_GENERIC,

    /**
     * Methods annotated with this message should return one or more of [Map].
     *
     * [toString] will be used to convert the keys and values of the map to String key value pairs.
     */
    RECORD_TYPE1,

    /**
     * Functions of this type should return a String or a stream of Strings.
     *
     * The requesting peer should then additionally request on the returned path. If a stream of
     * Strings is returned, then the remapping can be updated by emitting a second string, and the
     * requesting peer will request on the new path, discarding the previously mapped path.
     *
     * > Note that the returned path will not pass through `object-map` or authentication module
     * > mappings before being requested.
     */
    MAPPING,
  }
}
