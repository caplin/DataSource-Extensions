package com.caplin.integration.datasourcex.util

import java.nio.file.Path
import java.util.UUID

const val DEFAULT_DATASOURCE_NAME = "caplin-adapter"

sealed interface SimpleDataSourceConfig {
  val logDirectory: Path?
  val name: String
  val localLabel: String
  val extraConfig: String?

  class Discovery(
      val hostname: String,
      val clusterName: String = "caplin",
      override val name: String,
      override val logDirectory: Path?,
      override val localLabel: String = "$name-${UUID.randomUUID()}",
      override val extraConfig: String? = null,
  ) : SimpleDataSourceConfig {
    override fun toString(): String {
      return "Discovery(hostname='$hostname', clusterName='$clusterName', name='$name', " +
          "logDirectory=$logDirectory, localLabel='$localLabel', extraConfig=$extraConfig)"
    }
  }

  class Peer(
      override val name: String,
      override val logDirectory: Path? = null,
      override val localLabel: String = "$name-${UUID.randomUUID()}",
      override val extraConfig: String? = null,
      val incoming: Incoming? = null,
      val outgoing: List<Outgoing> = emptyList(),
      val requiredServices: List<String> = emptyList(),
      val devOverride: Boolean = false,
  ) : SimpleDataSourceConfig {

    init {
      check(incoming != null || outgoing.isNotEmpty()) {
        "Must configure at least one incoming or outgoing connection"
      }
    }

    class Outgoing(val hostname: String, val port: Int, val isWebsocket: Boolean) {
      override fun toString(): String {
        return "Outgoing(hostname='$hostname', port=$port, isWebsocket=$isWebsocket)"
      }
    }

    class Incoming(val port: Int, val isWebsocket: Boolean) {
      override fun toString(): String {
        return "Incoming(port=$port, isWebsocket=$isWebsocket)"
      }
    }

    override fun toString(): String {
      return "Peer(name='$name', logDirectory=$logDirectory, localLabel='$localLabel', " +
          "extraConfig=$extraConfig, incoming=$incoming, outgoing=$outgoing, " +
          "requiredServices=$requiredServices, devOverride=$devOverride)"
    }
  }
}
