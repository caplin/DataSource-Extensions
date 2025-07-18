package com.caplin.integration.datasourcex.reactive.api

import com.caplin.datasource.DataSource

interface BindContext {
  /** The [DataSource] that is currently being bound to. */
  val dataSource: DataSource
}
