package com.caplin.integration.datasourcex.util

import java.nio.file.Path
import java.util.UUID

/** Configuration for creating a DataSource via [SimpleDataSourceFactory]. */
sealed interface SimpleDataSourceConfig {
  /** The directory where log files will be written. */
  val logDirectory: Path?
  /** The name of the DataSource. */
  val name: String
  /** The local label for the DataSource. */
  val localLabel: String
  /** Any extra configuration to append to the configuration string. */
  val extraConfig: String?

  /** Configuration for a DataSource that connects to a discovery service. */
  class Discovery(
      /** The hostname of the discovery service. */
      val hostname: String,
      /** The cluster name to join. */
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

  /** Configuration for a DataSource that connects to specific peers. */
  class Peer(
      override val name: String,
      override val logDirectory: Path? = null,
      override val localLabel: String = "$name-${UUID.randomUUID()}",
      override val extraConfig: String? = null,
      /** Optional configuration for accepting incoming connections. */
      val incoming: Incoming? = null,
      /** List of outgoing peer connections. */
      val outgoing: List<Outgoing> = emptyList(),
      /** List of services required before this DataSource becomes active. */
      val requiredServices: List<String> = emptyList(),
      /** Whether to override development mode checks. */
      val devOverride: Boolean = false,
  ) : SimpleDataSourceConfig {

    init {
      check(incoming != null || outgoing.isNotEmpty()) {
        "Must configure at least one incoming or outgoing connection"
      }
    }

    /** Configuration for an outgoing peer connection. */
    class Outgoing(val hostname: String, val port: Int, val isWebsocket: Boolean) {
      override fun toString(): String {
        return "Outgoing(hostname='$hostname', port=$port, isWebsocket=$isWebsocket)"
      }
    }

    /** Configuration for accepting incoming connections. */
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
