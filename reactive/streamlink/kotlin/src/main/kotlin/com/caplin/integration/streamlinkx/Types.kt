package com.caplin.integration.streamlinkx

import com.caplin.integration.streamlinkx.ContainerChange.Clear
import com.caplin.integration.streamlinkx.ContainerChange.RowChange.Added
import com.caplin.integration.streamlinkx.ContainerChange.RowChange.Removed
import com.caplin.streamlink.ErrorReason
import com.caplin.streamlink.SubscriptionErrorType
import com.caplin.streamlink.SubscriptionStatusType
import com.caplin.streamlink.SubscriptionStatusType.STATUS_OK
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * An event emitted from a subscription or channel [Flow].
 *
 * One of [UpdateEvent] (carrying a [T] payload), [StatusEvent] (a subscription status change) or
 * [ErrorEvent] (a subscription error).
 */
sealed interface Event<out T>

/**
 * An [Event] stream carrying [ContainerChange]s, as produced by
 * [StreamLinkConnection.getContainer].
 */
typealias ContainerChangeEvent = Event<ContainerChange>

/**
 * An [Event] stream carrying record field maps, as produced by [StreamLinkConnection.getSubject].
 */
typealias RecordEvent = Event<Map<String, String>>

/** A single change to the membership of a container subscription. */
sealed interface ContainerChange {

  /** All rows were removed from the container. */
  data object Clear : ContainerChange

  /** A change affecting a single row at a given [index]. */
  sealed interface RowChange : ContainerChange {
    /** The row position the change applies to. */
    val index: Int
    /** The subject of the row that was added or removed. */
    val path: String

    /** A row for [path] was inserted at [index]. */
    class Added(
        override val index: Int,
        override val path: String,
    ) : RowChange {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Added

        if (index != other.index) return false
        if (path != other.path) return false

        return true
      }

      override fun hashCode(): Int {
        var result = index
        result = 31 * result + path.hashCode()
        return result
      }

      override fun toString(): String = "Added(index=$index, path='$path')"
    }

    /** The row for [path] at [index] was removed. */
    class Removed(
        override val index: Int,
        override val path: String,
    ) : RowChange {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Removed

        if (index != other.index) return false
        if (path != other.path) return false

        return true
      }

      override fun hashCode(): Int {
        var result = index
        result = 31 * result + path.hashCode()
        return result
      }

      override fun toString(): String = "Removed(index=$index, path='$path')"
    }
  }
}

/**
 * An [Event] carrying a data [payload] — a record update, container change or deserialised value.
 */
class UpdateEvent<out T>(val payload: T) : Event<T> {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as UpdateEvent<*>

    return payload == other.payload
  }

  override fun hashCode(): Int = payload?.hashCode() ?: 0

  override fun toString(): String = "UpdateEvent(payload=$payload)"
}

/**
 * An [Event] reporting a subscription status change.
 *
 * @property type the status type reported by the server (e.g. [STATUS_OK]).
 * @property message the accompanying status message, or empty if none.
 * @property fields any fields delivered alongside the status.
 */
class StatusEvent(
    val type: SubscriptionStatusType,
    val message: String,
    val fields: Map<String, String>,
) : Event<Nothing> {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StatusEvent

    if (type != other.type) return false
    if (message != other.message) return false
    if (fields != other.fields) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + message.hashCode()
    result = 31 * result + fields.hashCode()
    return result
  }

  override fun toString(): String = "StatusEvent(type=$type, message='$message', fields=$fields)"
}

/**
 * An [Event] reporting a subscription error, terminating the stream.
 *
 * @property type the category of error that occurred.
 * @property reason the detailed reason for the error.
 */
class ErrorEvent(
    val type: SubscriptionErrorType,
    val reason: ErrorReason,
) : Event<Nothing> {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ErrorEvent

    if (type != other.type) return false
    if (reason != other.reason) return false

    return true
  }

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + reason.hashCode()
    return result
  }

  override fun toString(): String = "ErrorEvent(type=$type, reason=$reason)"
}

/**
 * Folds a stream of [ContainerChange]s into successive snapshots of the container's row subjects,
 * emitting the updated list after each change.
 */
fun Flow<ContainerChange>.runningFold(): Flow<PersistentList<String>> =
    runningFold(persistentListOf()) { list, event ->
      when (event) {
        is Added -> list.adding(event.path)
        is Removed -> list.removing(event.path)
        Clear -> list.cleared()
      }
    }

/** Suspends until the stream emits a [StatusEvent] with type [STATUS_OK], returning it. */
suspend fun <T> Flow<Event<T>>.awaitStatusOk(): StatusEvent =
    filterIsInstance<StatusEvent>().first { it.type == STATUS_OK }

/** Keeps only [UpdateEvent]s from the stream, unwrapping each to its payload. */
fun <T> Flow<Event<T>>.filterIsUpdate(): Flow<T> =
    filterIsInstance<UpdateEvent<T>>().map { it.payload }
