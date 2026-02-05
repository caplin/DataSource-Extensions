package com.caplin.integration.datasourcex.spring.internal

import com.caplin.integration.datasourcex.reactive.api.ChannelType
import org.springframework.messaging.Message
import org.springframework.messaging.handler.AbstractMessageCondition

internal class DataSourceRequestTypeMessageCondition(val requestTypes: Set<RequestType>) :
    AbstractMessageCondition<DataSourceRequestTypeMessageCondition>() {

  companion object {
    fun channelFireAndForgetCondition(
        payloadType: Class<*>?,
        type: RequestType.Channel.ObjectType,
    ) =
        DataSourceRequestTypeMessageCondition(
            setOf(RequestType.Channel(ChannelType.UNIDIRECTIONAL_STREAM, payloadType, true, type))
        )

    fun channelRequestStreamCondition(
        payloadType: Class<*>?,
        type: RequestType.Channel.ObjectType,
    ) =
        DataSourceRequestTypeMessageCondition(
            setOf(RequestType.Channel(ChannelType.UNIDIRECTIONAL_STREAM, payloadType, false, type))
        )

    fun channelBidirectionalStreamCondition(
        payloadType: Class<*>?,
        type: RequestType.Channel.ObjectType,
    ) =
        DataSourceRequestTypeMessageCondition(
            setOf(RequestType.Channel(ChannelType.BIDIRECTIONAL_STREAM, payloadType, false, type))
        )

    fun streamUpdatingCondition(type: RequestType.Stream.ObjectType) =
        DataSourceRequestTypeMessageCondition(setOf(RequestType.Stream.Updating(type)))

    fun streamStaticCondition(type: RequestType.Stream.ObjectType) =
        DataSourceRequestTypeMessageCondition(setOf(RequestType.Stream.Static(type)))

    val emptyCondition = DataSourceRequestTypeMessageCondition(setOf())

    const val REQUEST_TYPE_HEADER = "dataSourceRequestType"
  }

  sealed interface RequestType {

    data class Channel(
        val channelType: ChannelType,
        val payloadType: Class<*>?,
        val fireAndForget: Boolean,
        val type: ObjectType,
    ) : RequestType {

      enum class ObjectType {
        JSON,
        TYPE1,
        GENERIC,
      }
    }

    sealed interface Stream : RequestType {
      enum class ObjectType {
        MAPPING,
        JSON,
        TYPE1,
        GENERIC,
        CONTAINER_JSON,
        CONTAINER_TYPE1,
        CONTAINER_GENERIC,
      }

      val type: ObjectType

      class Updating(override val type: ObjectType) : Stream {
        override fun toString(): String = this::class.simpleName!!
      }

      class Static(override val type: ObjectType) : Stream {
        override fun toString(): String = this::class.simpleName!!
      }
    }
  }

  override fun combine(
      other: DataSourceRequestTypeMessageCondition
  ): DataSourceRequestTypeMessageCondition {
    return if (this.requestTypes == other.requestTypes) other
    else DataSourceRequestTypeMessageCondition(requestTypes + other.requestTypes)
  }

  override fun getMatchingCondition(message: Message<*>): DataSourceRequestTypeMessageCondition? =
      when (val requestType = message.headers.get(REQUEST_TYPE_HEADER, RequestType::class.java)) {
        is RequestType.Channel -> {
          val payloadType = requestType.payloadType
          payloadType?.let {
            when (requestType.channelType) {
              ChannelType.UNIDIRECTIONAL_STREAM ->
                  if (requestType.fireAndForget) channelFireAndForgetCondition(it, requestType.type)
                  else channelRequestStreamCondition(it, requestType.type)

              ChannelType.BIDIRECTIONAL_STREAM ->
                  channelBidirectionalStreamCondition(it, requestType.type)
            }
          }
        }

        is RequestType.Stream.Updating -> streamUpdatingCondition(requestType.type)
        is RequestType.Stream.Static -> streamStaticCondition(requestType.type)
        null -> null
      }

  override fun getContent(): Collection<*> = this.requestTypes

  override fun getToStringInfix(): String = " || "

  override fun compareTo(other: DataSourceRequestTypeMessageCondition, message: Message<*>): Int =
      other.requestTypes.size - this.requestTypes.size
}
