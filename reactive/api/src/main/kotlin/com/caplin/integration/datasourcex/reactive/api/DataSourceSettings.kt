package com.caplin.integration.datasourcex.reactive.api

/** Process-wide settings for the reactive DataSource bindings. */
object DataSourceSettings {

  /**
   * Whether path variables populated by Liberator username object-mappings (`%u`/`%U`) are
   * URL-decoded when extracted from a subject. Defaults to `true`. Set to `false` when usernames
   * are injected without URL-encoding and must be left intact.
   */
  @JvmStatic var decodeUsernameObjectMappings: Boolean = true
}
