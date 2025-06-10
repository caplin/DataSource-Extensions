package com.caplin.integration.datasourcex.reactive.core

import com.caplin.datasource.channel.Channel
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.MessageFactory
import com.caplin.integration.datasourcex.reactive.api.RecordType

internal class RecordContext(
    private val images: Boolean = true,
    private val recordType: RecordType
) {
  internal fun Channel.createMessage(fields: Map<out Any, Any>) =
      when (recordType) {
        RecordType.GENERIC -> createMessage()
        RecordType.TYPE1 -> createRecordMessage()
      }.apply {
        fields.forEach { (key, value) -> setField(key.toString(), value.toString()) }
        isImage = images
      }

  internal fun CachedMessageFactory.createMessage(subject: String, fields: Map<out Any, Any>) =
      when (recordType) {
        RecordType.GENERIC -> createGenericMessage(subject)
        RecordType.TYPE1 -> createRecordType1Message(subject)
      }.apply {
        fields.forEach { (key, value) -> setField(key.toString(), value.toString()) }
        isImage = images
      }

  internal fun MessageFactory.createMessage(subject: String, fields: Map<out Any, Any>) =
      when (recordType) {
        RecordType.GENERIC -> createGenericMessage(subject)
        RecordType.TYPE1 -> createRecordType1Message(subject)
      }.apply {
        fields.forEach { (key, value) -> setField(key.toString(), value.toString()) }
        isImage = images
      }
}
