package com.caplin.integration.datasourcex.reactive.kotlin

import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {
  override var coroutineTestScope = true
}
