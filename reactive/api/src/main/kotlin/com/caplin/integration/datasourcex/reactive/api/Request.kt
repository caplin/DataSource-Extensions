package com.caplin.integration.datasourcex.reactive.api

/**
 * A decomposed subject request passed to a supplier for an active or container bind.
 *
 * @property path The full requested subject.
 * @property pathVariables The path variables extracted from the subject, keyed by name.
 * @property queryParameters The query parameters parsed from the subject's optional trailing
 *   `?a=b&c=d`, or empty when there is no query.
 */
class Request(
    val path: String,
    val pathVariables: Map<String, String>,
    val queryParameters: Map<String, String>,
)
