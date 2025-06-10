package com.caplin.integration.datasourcex.spring.internal

import com.caplin.datasource.DataSource
import org.springframework.context.ApplicationEvent

internal class DataSourceServerInitializedEvent(dataSource: DataSource) :
    ApplicationEvent(dataSource) {
  val dataSource: DataSource
    get() = getSource()

  override fun getSource(): DataSource = super.getSource() as DataSource
}
