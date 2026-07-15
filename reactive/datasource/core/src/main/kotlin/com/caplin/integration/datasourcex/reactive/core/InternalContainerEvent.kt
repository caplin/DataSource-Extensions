package com.caplin.integration.datasourcex.reactive.core

internal sealed interface InternalContainerEvent {
  val path: String

  data class Inserted(override val path: String) : InternalContainerEvent

  data class Removed(override val path: String) : InternalContainerEvent
}
