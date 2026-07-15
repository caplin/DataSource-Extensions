package com.caplin.integration.datasourcex.reactive.api

/** Process-wide settings for the reactive DataSource bindings. */
object DataSourceSettings {

  /**
   * Whether path variables populated by Liberator username object-mappings (`%u`/`%U`) are
   * URL-decoded when extracted from a subject. Defaults to `false`.
   */
  @JvmStatic var decodeUsernameObjectMappings: Boolean = false
}
