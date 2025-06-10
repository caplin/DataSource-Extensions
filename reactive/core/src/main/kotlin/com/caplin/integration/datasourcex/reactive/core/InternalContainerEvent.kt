package com.caplin.integration.datasourcex.reactive.core

internal sealed interface InternalContainerEvent {
  val subject: String

  data class Inserted(override val subject: String) : InternalContainerEvent

  data class Removed(override val subject: String) : InternalContainerEvent
}
