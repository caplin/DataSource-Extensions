package com.caplin.integration.datasourcex.reactive.api

/**
 * A JSON tree from any JSON library, which a JSON bind carries without serialising it again when
 * the DataSource's `JsonHandler` uses the same library.
 *
 * Send one from a JSON active subject, container, or channel: a tree of the handler's library goes
 * to the handler as it stands, and any other tree is converted through its `toString()`, which has
 * to be its JSON text (as Jackson's and Gson's are). Bind a JSON channel's receive type to
 * [JsonTree] and each inbound payload arrives as the handler's tree.
 */
public data class JsonTree(public val tree: Any)
