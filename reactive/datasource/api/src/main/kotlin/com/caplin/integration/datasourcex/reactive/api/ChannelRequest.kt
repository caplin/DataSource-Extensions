package com.caplin.integration.datasourcex.reactive.api

import com.caplin.integration.datasourcex.util.Subject
import java.util.SortedMap

/**
 * A decomposed subject request passed to a supplier for a channel bind.
 *
 * @property pathParameters The subject's path parts, in order.
 * @property pathVariables The path variables extracted from the subject, keyed by name, in pattern
 *   order.
 * @property queryParameters The query parameters parsed from the subject's optional trailing
 *   `?a=b&c=d`, or empty when there is no query.
 * @property receive The flow of messages received from the client on this channel.
 */
class ChannelRequest<R>(
    override val pathParameters: List<String>,
    val pathVariables: LinkedHashMap<String, String>,
    override val queryParameters: SortedMap<String, String>,
    val receive: R,
) : Subject
