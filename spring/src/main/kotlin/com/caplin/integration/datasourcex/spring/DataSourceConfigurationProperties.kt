package com.caplin.integration.datasourcex.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.core.io.Resource

@ConfigurationProperties(prefix = "caplin.datasource")
class DataSourceConfigurationProperties
@ConstructorBinding
constructor(
    /** Specifies the configuration for the "provided" mode. */
    val provided: Provided?,
    /** Specifies the configuration for the "managed" mode. */
    val managed: Managed?
) {
  init {
    check(provided == null || managed == null) {
      "Should specify only one of caplin.datasource.provided and caplin.datasource.managed"
    }
  }

  class Provided(
      /** Autowires a DataSource using a provided datasource.conf file. */
      val configurationFile: Resource,

      /**
       * The remote label pattern to use for dynamically generated services. If unspecified, then
       * this falls back to the datasrc-local-label value from the provided datasource.conf.
       */
      val remoteLabelPattern: String?
  ) {
    override fun toString(): String {
      return "Provided(configurationFile=$configurationFile, remoteLabelPattern=$remoteLabelPattern)"
    }
  }

  class Managed(
      /**
       * The name for the DataSource. If unspecified, then this falls back to the value of
       * spring.application.name and then to the fixed value "caplin-adapter". This is also used to
       * generate a label by which this peer is known as by other processes in the format
       * <name>-<uuid> where the name is stripped of non-alphanumeric characters.
       */
      val name: String?,
      val discovery: Discovery?,
      val peer: Peer?,

      /**
       * The path to write DataSource logs to. If unspecified, then this writes to a temporary
       * directory which is logged on startup.
       */
      val logDirectory: String?,
      /**
       * Additional configuration to pass to the managed DataSource as documented here:
       * https://www.caplin.com/developer/caplin-platform/datasource/datasource-datasource-configuration-reference-introduction#links-to-the-datasource-configuration-reference-pages
       */
      val extraConfigurationFile: Resource?,
  ) {
    init {
      check(discovery == null && peer != null || discovery != null && peer == null) {
        "Should not specify both caplin.datasource.managed.discovery and caplin.datasource.managed.peer"
      }
    }

    override fun toString(): String {
      return "Managed(name=$name, discovery=$discovery, peer=$peer, logDirectory=$logDirectory, " +
          "extraConfigurationFile=$extraConfigurationFile)"
    }
  }

  class Discovery(
      /** The hostname of the Discovery server. */
      val hostname: String,
      /** The name of the Discovery cluster. */
      val clusterName: String = "caplin"
  ) {
    override fun toString(): String {
      return "Discovery(hostname='$hostname', clusterName='$clusterName')"
    }
  }

  class Peer(
      val incoming: Incoming?,
      /**
       * A list of outgoing peer connections in the format [ws://]<hostname>:<port>. If the ws://
       * prefix is not present, the connection will be a plain TCP connection.
       */
      val outgoing: List<String> = emptyList(),
      /** A list of services required */
      val requiredServices: List<String> = emptyList(),
      /** All connections from this peer should act as a development override for the remote side */
      val devOverride: Boolean = false,
  ) {
    override fun toString(): String {
      return "Peer(incoming=$incoming, outgoing=$outgoing, requiredServices=$requiredServices, " +
          "devOverride=$devOverride)"
    }
  }

  class Incoming(
      /**
       * The port that DataSource should accept incoming peer connections on. If unspecified, this
       * DataSource will not accept incoming connections.
       */
      val port: Int,
      /** Whether the incoming port should allow websocket or plain TCP connections. */
      val websocket: Boolean = true
  ) {
    override fun toString(): String {
      return "Incoming(port=$port, websocket=$websocket)"
    }
  }

  override fun toString(): String {
    return "DataSourceConfigurationProperties(provided=$provided, managed=$managed)"
  }
}
