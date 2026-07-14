package com.caplin.integration.streamlinkx

import com.caplin.streamlink.BinaryEvent
import com.caplin.streamlink.ChatEvent
import com.caplin.streamlink.ContainerEvent
import com.caplin.streamlink.DirectoryEvent
import com.caplin.streamlink.JsonEvent
import com.caplin.streamlink.NewsEvent
import com.caplin.streamlink.PageEvent
import com.caplin.streamlink.PermissionEvent
import com.caplin.streamlink.RecordType1Event
import com.caplin.streamlink.RecordType2Event
import com.caplin.streamlink.RecordType3Event
import com.caplin.streamlink.StoryEvent
import com.caplin.streamlink.Subscription
import com.caplin.streamlink.SubscriptionErrorEvent
import com.caplin.streamlink.SubscriptionListener
import com.caplin.streamlink.SubscriptionStatusEvent
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking

internal class DefaultSubscriptionListener(private val channel: SendChannel<Event<Nothing>>) :
    SubscriptionListener {

  override fun onRecordUpdate(subscription: Subscription, event: RecordType1Event) {
    //
  }

  override fun onRecordType2Update(subscription: Subscription, event: RecordType2Event) {
    //
  }

  override fun onRecordType3Update(subscription: Subscription, event: RecordType3Event) {
    //
  }

  override fun onPermissionUpdate(subscription: Subscription, event: PermissionEvent) {
    //
  }

  override fun onNewsUpdate(subscription: Subscription, event: NewsEvent) {
    //
  }

  override fun onStoryUpdate(subscription: Subscription, event: StoryEvent) {
    //
  }

  override fun onPageUpdate(subscription: Subscription, event: PageEvent) {
    //
  }

  override fun onChatUpdate(subscription: Subscription, event: ChatEvent) {
    //
  }

  override fun onDirectoryUpdate(subscription: Subscription, event: DirectoryEvent) {
    //
  }

  override fun onContainerUpdate(subscription: Subscription, event: ContainerEvent) {
    //
  }

  override fun onJsonUpdate(subscription: Subscription, event: JsonEvent) {
    //
  }

  override fun onBinaryUpdate(p0: Subscription?, p1: BinaryEvent?) {
    //
  }

  override fun onSubscriptionError(subscription: Subscription, event: SubscriptionErrorEvent) {
    channel.trySendBlocking(ErrorEvent(event.error, event.errorReason))
    channel.close()
  }

  override fun onSubscriptionStatus(subscription: Subscription, event: SubscriptionStatusEvent) {
    channel.trySendBlocking(
        StatusEvent(
            event.status,
            event.statusMessage.orEmpty(),
            event.fields.toPersistentMap(),
        ),
    )
  }
}
