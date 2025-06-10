package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import com.caplin.datasource.internal.configuration.AttributeConfiguration.DATASRC_LOCAL_LABEL
import com.caplin.datasource.internal.configuration.AttributeConfiguration.DATASRC_NAME
import com.caplin.datasource.messaging.json.JacksonJsonHandler
import com.caplin.integration.datasourcex.spring.DataSourceConfigurationProperties
import com.caplin.integration.datasourcex.util.DEFAULT_DATASOURCE_NAME
import com.caplin.integration.datasourcex.util.SimpleDataSourceConfig.Discovery
import com.caplin.integration.datasourcex.util.SimpleDataSourceConfig.Peer
import com.caplin.integration.datasourcex.util.SimpleDatasourceFactory.createDataSource
import com.caplin.integration.datasourcex.util.getLogger
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.UUID
import java.util.logging.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(DataSourceConfigurationProperties::class)
internal class DataSourceAutoConfiguration {

  private val logger = getLogger<DataSourceAutoConfiguration>()

  companion object {
    internal val strippedRemoteLabelCharacters = "[^A-Za-z0-9-_]".toRegex()
  }

  @Bean
  @ConditionalOnMissingBean
  fun dataSource(
      objectMapper: ObjectMapper,
      dataSourceConfigurationProperties: DataSourceConfigurationProperties,
      @Value("\${spring.application.name:#{null}}") applicationName: String?,
  ): DataSource {
    val provided = dataSourceConfigurationProperties.provided
    val managed = dataSourceConfigurationProperties.managed
    val configurationFile = provided?.configurationFile
    check(configurationFile != null || managed != null) {
      "Must provide a DataSource or specify one of caplin.datasource.provided.configurationFile " +
          "or caplin.datasource.managed"
    }
    return if (configurationFile != null) {
          DataSource.fromConfigString(
              provided.configurationFile.inputStream.bufferedReader().readText(),
              Logger.getLogger(DataSource::class.qualifiedName),
          )
        } else {
          val managedDataSourceInfo = createManagedDataSourceInfo(applicationName, managed!!)
          val name = managedDataSourceInfo.name
          val label = managedDataSourceInfo.label
          createDataSource(
              managed.peer?.let { peer ->
                Peer(
                    name = name,
                    localLabel = label,
                    logDirectory = managed.logDirectory?.let { Paths.get(it) },
                    requiredServices = peer.requiredServices,
                    devOverride = peer.devOverride,
                    incoming = peer.incoming?.let { Peer.Incoming(it.port, it.websocket) },
                    outgoing =
                        peer.outgoing
                            .asSequence()
                            .map(String::trim)
                            .map { address ->
                              val (hostname, port) = address.removePrefix("ws://").split(":")
                              Peer.Outgoing(hostname, port.toInt(), address.startsWith("ws://"))
                            }
                            .toList(),
                    extraConfig =
                        managed.extraConfigurationFile?.inputStream?.bufferedReader()?.readText(),
                )
              }
                  ?: managed.discovery!!.let { discovery ->
                    Discovery(
                        name = name,
                        localLabel = label,
                        hostname = discovery.hostname,
                        clusterName = discovery.clusterName,
                        logDirectory = managed.logDirectory?.let { Paths.get(it) },
                        extraConfig =
                            managed.extraConfigurationFile
                                ?.inputStream
                                ?.bufferedReader()
                                ?.readText(),
                    )
                  })
        }
        .apply {
          extraConfiguration.jsonHandler =
              JacksonJsonHandler(
                  Logger.getLogger(JacksonJsonHandler::class.qualifiedName), objectMapper)
        }
  }

  @Bean
  fun dataSourceInfo(
      dataSource: DataSource,
      dataSourceConfigurationProperties: DataSourceConfigurationProperties,
      @Value("\${spring.application.name:#{null}}") applicationName: String?,
  ): DataSourceInfo =
      (dataSourceConfigurationProperties.managed?.let {
            createManagedDataSourceInfo(applicationName, it)
          }
              ?: run {
                val label =
                    checkNotNull(
                        dataSource.configuration.getStringValue(DATASRC_LOCAL_LABEL).takeIf {
                          it.isNotBlank()
                        }) {
                          "$DATASRC_LOCAL_LABEL must be set"
                        }
                DataSourceInfo(
                    name =
                        checkNotNull(
                            dataSource.configuration.getStringValue(DATASRC_NAME).takeIf {
                              it.isNotBlank()
                            }) {
                              "$DATASRC_NAME must be set"
                            },
                    label = label,
                    remoteLabelPattern =
                        dataSourceConfigurationProperties.provided?.remoteLabelPattern ?: label,
                )
              })
          .also { logger.info { "Detected $it" } }

  private fun createManagedDataSourceInfo(
      applicationName: String?,
      managed: DataSourceConfigurationProperties.Managed,
  ): DataSourceInfo {
    val name =
        (managed.name ?: applicationName ?: DEFAULT_DATASOURCE_NAME).replace(
            strippedRemoteLabelCharacters, "")
    return DataSourceInfo(
        name = name,
        label = "$name-${UUID.randomUUID()}",
        remoteLabelPattern =
            """^$name-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""",
    )
  }
}
