package com.caplin.integration.datasourcex.reactive.api

import java.time.Duration

sealed class ActiveContainerConfig {

  companion object {
    const val STRUCTURE_DEBOUNCE_DEFAULT: Long = 100L
    const val ROW_REQUEST_TIMEOUT_DEFAULT: Long = 10000L

    const val DEFAULT_ITEMS_SUFFIX = "-items"
  }

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

  /** Whether rows should be inserted at the head or the tail of the container. */
  var insertAt: InsertAt = InsertAt.TAIL

  /** Changes to the container structure are debounced and conflated. */
  var structureDebounce: Duration = Duration.ofMillis(STRUCTURE_DEBOUNCE_DEFAULT)

  /**
   * How long to wait for a row request before timing out.
   *
   * This typically only matters for recovery scenarios where the rows may be re-requested before
   * the container.
   */
  var rowRequestTimeout: Duration = Duration.ofMillis(ROW_REQUEST_TIMEOUT_DEFAULT)

  /**
   * Suffix that will be appended to the container paths to produce the row paths.
   *
   * For example, a [rowPathSuffix] of `-items` and a container path of `container/a` will produce
   * items with the paths `/container/a-items/{rowKey}` where `{rowKey}` is provided by
   * [ContainerEvent.RowEvent.key].
   */
  var rowPathSuffix: String = DEFAULT_ITEMS_SUFFIX

  class Json : ActiveContainerConfig()

  class Record : ActiveContainerConfig() {
    var rowImages: Boolean = false
    var rowRecordType: RecordType = RecordType.GENERIC
  }
}
