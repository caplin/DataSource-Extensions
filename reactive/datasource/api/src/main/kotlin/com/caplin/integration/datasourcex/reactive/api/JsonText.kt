package com.caplin.integration.datasourcex.reactive.api

/**
 * JSON text a JSON bind carries as it stands, so the caller serialises it rather than the
 * DataSource's `JsonHandler`.
 *
 * Send one from a JSON active subject, container, or channel: it is parsed by whichever
 * `JsonHandler` the DataSource holds at send time. Bind a JSON channel's receive type to [JsonText]
 * and each inbound message arrives as its payload's JSON text.
 */
public data class JsonText(public val json: String)
