package com.caplin.integration.datasourcex.util

import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {
  override var coroutineTestScope = true
}
