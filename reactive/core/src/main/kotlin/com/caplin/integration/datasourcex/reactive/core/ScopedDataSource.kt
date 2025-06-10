package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.DataSource
import com.caplin.integration.datasourcex.util.getLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

class ScopedDataSource
internal constructor(
    private val dataSource: DataSource,
    private val coroutineScope: CoroutineScope
) : DataSource by dataSource, CoroutineScope by coroutineScope {

  companion object {
    private val logger = getLogger<ScopedDataSource>()

    operator fun invoke(dataSource: DataSource, parentScope: CoroutineScope): ScopedDataSource =
        ScopedDataSource(
            dataSource,
            parentScope +
                SupervisorJob() +
                CoroutineExceptionHandler { _, throwable ->
                  logger.warn(throwable) { "Unhandled exception" }
                },
        )
  }
}
