package com.caplin.integration.datasourcex.util

import com.caplin.datasource.DataSource
import com.caplin.datasource.messaging.json.JsonHandler
import com.caplin.integration.datasourcex.util.SimpleDataSourceFactory.defaultJackson2ObjectMapper
import com.caplin.integration.datasourcex.util.serialization.jackson2.Jackson2JsonHandler
import com.caplin.integration.datasourcex.util.serialization.jackson2.registerDataSourceModule
import com.caplin.integration.datasourcex.util.serialization.jackson3.Jackson3JsonHandler
import com.caplin.integration.datasourcex.util.serialization.jackson3.addDataSourceModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.util.logging.Logger
import tools.jackson.databind.ObjectMapper as Jackson3ObjectMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

/**
 * A factory for creating [DataSource] instances from simplified configurations. Allows easy setup
 * for tests and examples.
 */
object SimpleDataSourceFactory {

  private const val MAX_PATH_LENGTH = 32

  private val logger = getLogger<SimpleDataSourceFactory>()

  /**
   * The default [ObjectMapper] used for serializing and deserializing JSON payloads. It is
   * pre-configured with the JavaTime module and DataSource serialization extensions.
   */
  val defaultJackson2ObjectMapper: ObjectMapper by lazy {
    jacksonObjectMapper()
        .configure(WRITE_DATES_AS_TIMESTAMPS, false)
        .registerModule(JavaTimeModule())
        .registerDataSourceModule()
  }

  @JvmStatic
  fun createJackson2JsonHandler(objectMapper: ObjectMapper): Jackson2JsonHandler =
      Jackson2JsonHandler(objectMapper)

  @Suppress("unused")
  val defaultJackson2JsonHandler: Jackson2JsonHandler by lazy {
    createJackson2JsonHandler(defaultJackson2ObjectMapper)
  }

  val defaultJackson3ObjectMapper: Jackson3ObjectMapper by lazy {
    jacksonMapperBuilder().addDataSourceModule().build()
  }

  @JvmStatic
  fun createJackson3JsonHandler(objectMapper: Jackson3ObjectMapper): Jackson3JsonHandler =
      Jackson3JsonHandler(objectMapper)

  val defaultJackson3JsonHandler: Jackson3JsonHandler by lazy {
    createJackson3JsonHandler(defaultJackson3ObjectMapper)
  }

  /**
   * Creates a data source based on the given simple configuration.
   *
   * @param simpleConfig The simple configuration for the data source.
   * @param jsonHandler The [JsonHandler] to use for serializing and deserializing JSON payloads.
   *   This defaults to the Jackson 3 [defaultJackson3JsonHandler] backed by
   *   [defaultJackson3ObjectMapper].
   * @return The created data source.
   */
  @JvmStatic
  fun createDataSource(
      simpleConfig: SimpleDataSourceConfig,
      jsonHandler: JsonHandler<*> = defaultJackson3JsonHandler,
  ): DataSource {
    val logPath =
        simpleConfig.logDirectory
            ?: run {
              val tmpLogPath =
                  Files.createTempDirectory(
                      simpleConfig.name.replace("\\s".toRegex(), "").take(MAX_PATH_LENGTH),
                  )
              logger.warn {
                "log file path is not specified, writing datasource logs to $tmpLogPath"
              }
              tmpLogPath
            }
    val logDirectory = logPath.toFile()
    check(!logDirectory.exists() || logDirectory.isDirectory) { "$logPath is not a directory" }
    logDirectory.mkdirs()

    val peerConfiguration =
        when (simpleConfig) {
          is SimpleDataSourceConfig.Discovery ->
              """
                |discovery-addr         ${simpleConfig.hostname}
                |discovery-cluster-name ${simpleConfig.clusterName}
                |datasrc-name           ${simpleConfig.name}
                |datasrc-local-label    ${simpleConfig.localLabel}
                """

          is SimpleDataSourceConfig.Peer ->
              """
                |datasrc-name         ${simpleConfig.name}
                |datasrc-local-label  ${simpleConfig.localLabel}
                |datasrc-dev-override ${simpleConfig.devOverride}
                |discovery-require-service ${simpleConfig.requiredServices.joinToString(" ")}
                ${
              simpleConfig.incoming?.let {
                """
                        |${if (it.isWebsocket) "datasrc-ws-port" else "datasrc-port"} ${it.port}
                        """
              }.orEmpty()
            }
                ${
              simpleConfig.outgoing.joinToString("\n") {
                """
                        |add-peer
                        |    addr         ${it.hostname}
                        |    port         ${it.port}
                        |    local-type   active|contrib
                        |    websocket    ${it.isWebsocket}
                        |end-peer
                        """
              }
            }
                """
        }

    // Limit packet log to a max size of 500MB, keep 10 of them -> 5 gb (ish) in total
    val logConfig =
        """
            |datasrc-pkt-log packet-%a.log
            |event-log event-%a.log
            |log-level INFO
            |log-dir ${logPath.toString().replace("\\", "\\\\")}
            |log-maxfiles 10
            |log-use-parent-handlers TRUE
            |add-log
            |       name                packet_log
            |       period              1
            |       suffix              .%w.%H.%M
            |       maxsize             500000000
            |end-log
            """

    val config =
        """
            $logConfig
            
            $peerConfiguration
            
            ${simpleConfig.extraConfig.orEmpty()}
            """
            .trimMargin()

    return DataSource.fromConfigString(config, Logger.getLogger(DataSource::class.qualifiedName))
        .apply { extraConfiguration.jsonHandler = jsonHandler }
  }
}
