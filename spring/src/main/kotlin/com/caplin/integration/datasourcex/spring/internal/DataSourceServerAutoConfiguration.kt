package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
internal class DataSourceServerAutoConfiguration {
  @ConditionalOnMissingBean
  @Bean
  fun dataSourceMessageHandler(): DataSourceMessageHandler = DataSourceMessageHandler()

  @ConditionalOnMissingBean
  @Bean
  fun dataSourceServerBootstrap(
      dataSource: DataSource,
      dataSourceMessageHandler: DataSourceMessageHandler,
      dataSourceInfo: DataSourceInfo,
      @Value($$"${caplin.datasource.decode-username-object-mappings:true}")
      decodeUsernameObjectMappings: Boolean,
  ): DataSourceServerBootstrap =
      DataSourceServerBootstrap(
          dataSource,
          dataSourceMessageHandler,
          dataSourceInfo,
          decodeUsernameObjectMappings,
      )
}
