package com.caplin.integration.datasourcex.reactive.api

import com.caplin.integration.datasourcex.util.Subject

/**
 * A decomposed subject request passed to a supplier for a channel bind.
 *
 * @property path The full requested subject.
 * @property pathVariables The path variables extracted from the subject, keyed by name, in pattern
 *   order (which backs [pathParameters]).
 * @property queryParameters The query parameters parsed from the subject's optional trailing
 *   `?a=b&c=d`, or empty when there is no query.
 * @property receive The flow of messages received from the client on this channel.
 */
class ChannelRequest<R>(
    override val path: String,
    val pathVariables: LinkedHashMap<String, String>,
    override val queryParameters: Map<String, String>,
    val receive: R,
) : Subject {
  override val pathParameters: List<String>
    get() = pathVariables.values.toList()
}
