package com.caplin.integration.datasourcex.util

import com.caplin.datasource.DataSource
import com.caplin.datasource.messaging.json.JacksonJsonHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.util.logging.Logger

object SimpleDatasourceFactory {

  private const val MAX_PATH_LENGTH = 32

  private val logger = getLogger<SimpleDatasourceFactory>()

  val defaultObjectMapper: ObjectMapper =
      jacksonObjectMapper()
          .configure(WRITE_DATES_AS_TIMESTAMPS, false)
          .registerModule(JavaTimeModule())

  /**
   * Creates a data source based on the given simple configuration.
   *
   * @param simpleConfig The simple configuration for the data source.
   * @return The created data source.
   */
  @JvmStatic
  fun createDataSource(
      simpleConfig: SimpleDataSourceConfig,
      objectMapper: ObjectMapper = defaultObjectMapper
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
        .apply {
          extraConfiguration.jsonHandler =
              JacksonJsonHandler(
                  Logger.getLogger(JacksonJsonHandler::class.qualifiedName),
                  objectMapper,
              )
        }
  }
}
