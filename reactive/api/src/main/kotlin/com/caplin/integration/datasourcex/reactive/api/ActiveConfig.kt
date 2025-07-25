package com.caplin.integration.datasourcex.reactive.api

sealed class ActiveConfig {

  /**
   * Object mappings to be applied by Liberator.
   *
   * For a patten `/{username}/my-subject` you could provide a map of `{ "username": "%u" }` to have
   * Liberator map the username into the `{username}` placeholder.
   *
   * A request of `/my-subject` by a user named `john.connor` would be made as
   * `/john.connor/my-subject`.
   *
   * Please ensure that usernames provided to Liberator are appropriately URL Encoded if they are to
   * contain special characters - especially the `/` path separator.
   */
  var objectMappings: Map<String, String>? = null

  class Json : ActiveConfig()

  class Record : ActiveConfig() {
    var images: Boolean = false
    var recordType: RecordType = RecordType.GENERIC
  }

  class Mapping : ActiveConfig()
}
