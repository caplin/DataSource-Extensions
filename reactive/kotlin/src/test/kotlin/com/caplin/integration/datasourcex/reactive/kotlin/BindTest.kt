@file:OptIn(ExperimentalCoroutinesApi::class)

package com.caplin.integration.datasourcex.reactive.kotlin

import com.caplin.datasource.DataSource
import com.caplin.datasource.SubjectError.NotFound
import com.caplin.datasource.SubjectErrorEvent
import com.caplin.datasource.messaging.CachedMessageFactory
import com.caplin.datasource.messaging.container.ContainerMessage
import com.caplin.datasource.messaging.json.JsonMessage
import com.caplin.datasource.messaging.record.GenericMessage
import com.caplin.datasource.namespace.RegexNamespace
import com.caplin.datasource.publisher.CachingDataProvider
import com.caplin.datasource.publisher.CachingPublisher
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent.RowEvent.Remove
import com.caplin.integration.datasourcex.reactive.api.ContainerEvent.RowEvent.Upsert
import com.caplin.integration.datasourcex.reactive.api.InsertAt.HEAD
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Completion
import com.caplin.integration.datasourcex.util.flow.ValueOrCompletion.Value
import com.caplin.integration.datasourcex.util.flow.dematerialize
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class BindTest :
    FunSpec({
      isolationMode = IsolationMode.InstancePerTest

      suspend fun <T : Any?> MutableSharedFlow<ValueOrCompletion<T>>.emitUpdate(event: T) {
        emit(Value(event))
      }

      val dataProviders = mutableListOf<CachingDataProvider>()

      val dataProviderCaptor: CachingDataProvider by ReadOnlyProperty { _, _ ->
        dataProviders.first()
      }

      val containerDataProviderCaptor: CachingDataProvider by ReadOnlyProperty { _, _ ->
        dataProviders.drop(1).first()
      }

      val containerMessages = LoadingList<ContainerMessage> { mockk(relaxUnitFun = true) }
      val genericMessages = LoadingList<GenericMessage> { mockk(relaxUnitFun = true) }
      val jsonMessages = LoadingList<JsonMessage> { mockk(relaxUnitFun = true) }
      val subjectErrorEvents = LoadingList<SubjectErrorEvent> { mockk() }

      val cachedMessageFactory =
          mockk<CachedMessageFactory> {
            every { createContainerMessage(any()) } answers { containerMessages.next() }
            every { createGenericMessage(any()) } answers { genericMessages.next() }
            every { createJsonMessage(any(), any()) } answers { jsonMessages.next() }
            every { createSubjectErrorEvent(any(), any()) } answers { subjectErrorEvents.next() }
          }

      val cachingPublisher =
          mockk<CachingPublisher> {
            every { this@mockk.cachedMessageFactory } returns cachedMessageFactory
            every { publishSubjectErrorEvent(any()) } just Runs
            every { publish(any()) } just Runs
          }

      val dataSource =
          mockk<DataSource> {
            every { createCachingPublisher(any<RegexNamespace>(), capture(dataProviders)) } answers
                {
                  secondArg<CachingDataProvider>().setPublisher(cachingPublisher)
                  cachingPublisher
                }
          }

      val requestedContainerSubjects =
          mutableMapOf<
              String,
              MutableSharedFlow<ValueOrCompletion<ContainerEvent<Map<String, String>>>>,
          >()
      val genericContainerFunction:
          (String, Map<String, String>) -> Flow<ContainerEvent<Map<String, String>>> =
          { containerSubject: String, _: Map<String, String> ->
            MutableSharedFlow<ValueOrCompletion<ContainerEvent<Map<String, String>>>>()
                .also { requestedContainerSubjects[containerSubject] = it }
                .dematerialize()
          }

      val requestedGenericSubjects =
          ConcurrentHashMap<String, MutableSharedFlow<ValueOrCompletion<Map<String, String>>>>()
      val genericSubjectFunction: (String, Map<String, String>) -> Flow<Map<String, String>> =
          { subject: String, _: Map<String, String> ->
            MutableSharedFlow<ValueOrCompletion<Map<String, String>>>()
                .also { requestedGenericSubjects[subject] = it }
                .dematerialize()
          }

      val requestedJsonSubjects = mutableMapOf<String, MutableSharedFlow<ValueOrCompletion<Any>>>()
      val jsonSubjectFunction: (String, Map<String, String>) -> Flow<Any> =
          { subject: String, _: Map<String, String> ->
            MutableSharedFlow<ValueOrCompletion<Any>>()
                .also { requestedJsonSubjects[subject] = it }
                .dematerialize()
          }

      test("Generic Container - Emits empty update after timeout") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record {
              pattern(
                  pattern = "/SUBJECT/**",
                  configure = { structureDebounce = Duration.ofMillis(10) },
                  supplier = genericContainerFunction,
              )
            }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")

        delay(10)

        verify(exactly = 0) { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }

        delay(1)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }

        val message = containerMessages.single()

        verify(exactly = 0) { message.addElement(any()) }
        verify(exactly = 0) { message.insertElement(any(), any()) }

        verify { cachingPublisher.publish(message) }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
      }

      test("Generic Container - Default add and removals") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record { pattern(pattern = "/SUBJECT/**", supplier = genericContainerFunction) }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")

        delay(1)
        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf("1" to "a", "2" to "b")))

        delay(50)

        sharedFlow.emitUpdate(Upsert(key = "2", value = mapOf("1" to "x", "2" to "y")))

        verify(exactly = 0) { cachingPublisher.publish(any()) }
        delay(50)

        verify(exactly = 0) { cachingPublisher.publish(any()) }
        delay(51)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }
        containerMessages.last().also { containerMessage ->
          verifyOrder {
            containerMessage.addElement("/SUBJECT/1-items/1")
            containerMessage.addElement("/SUBJECT/1-items/2")
            cachingPublisher.publish(containerMessage)
          }
        }

        dataProviderCaptor.onRequest("/SUBJECT/1-items/1")
        delay(1)

        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1-items/1") }
        genericMessages.last().also { genericMessage ->
          verifyOrder {
            genericMessage.setField("1", "a")
            genericMessage.setField("2", "b")
            cachingPublisher.publish(genericMessage)
          }
        }

        dataProviderCaptor.onRequest("/SUBJECT/1-items/2")
        delay(1)

        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1-items/2") }
        genericMessages.last().also { genericMessage ->
          verifyOrder {
            genericMessage.setField("1", "x")
            genericMessage.setField("2", "y")
            cachingPublisher.publish(genericMessage)
          }
        }

        sharedFlow.emitUpdate(Remove(key = "2"))

        delay(50)

        sharedFlow.emitUpdate(Upsert(key = "3", value = mapOf("1" to "l", "2" to "m")))

        delay(101)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }
        containerMessages.last().also { containerMessage ->
          verifyOrder {
            containerMessage.addElement("/SUBJECT/1-items/3")
            containerMessage.removeElement("/SUBJECT/1-items/2")
            cachingPublisher.publish(containerMessage)
          }
        }

        dataProviderCaptor.onRequest("/SUBJECT/1-items/3")
        delay(1)

        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1-items/3") }
        genericMessages.last().also { genericMessage ->
          verifyOrder {
            genericMessage.setField("1", "l")
            genericMessage.setField("2", "m")
            cachingPublisher.publish(genericMessage)
          }
        }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
        dataProviderCaptor.onDiscard("/SUBJECT/1-items/1")
        dataProviderCaptor.onDiscard("/SUBJECT/1-items/2")
        dataProviderCaptor.onDiscard("/SUBJECT/1-items/3")
      }

      test("Generic Container - Insert at zero") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record {
              pattern(
                  pattern = "/SUBJECT/**",
                  configure = { insertAt = HEAD },
                  supplier = genericContainerFunction,
              )
            }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))

        delay(50)

        sharedFlow.emitUpdate(Upsert(key = "2", value = mapOf()))

        delay(101)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }
        containerMessages.last().also { containerMessage ->
          verifyOrder {
            containerMessage.insertElement("/SUBJECT/1-items/1", 0)
            containerMessage.insertElement("/SUBJECT/1-items/2", 0)
            cachingPublisher.publish(containerMessage)
          }
        }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
        delay(1)
      }

      test("Generic Container - Re-request") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record {
              pattern(
                  pattern = "/SUBJECT/**",
                  configure = { insertAt = HEAD },
                  supplier = genericContainerFunction,
              )
            }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))

        delay(50)

        sharedFlow.emitUpdate(Upsert(key = "2", value = mapOf()))

        delay(101)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }
        containerMessages.last().also { containerMessage ->
          verifyOrder {
            containerMessage.insertElement("/SUBJECT/1-items/1", 0)
            containerMessage.insertElement("/SUBJECT/1-items/2", 0)
            cachingPublisher.publish(containerMessage)
          }
        }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
        delay(1)

        // Resubscribe

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow2 = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow2.emitUpdate(Upsert(key = "1", value = mapOf()))

        delay(50)

        sharedFlow2.emitUpdate(Upsert(key = "2", value = mapOf()))

        delay(101)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }

        containerMessages.last().also { containerMessage ->
          verifyOrder {
            containerMessage.insertElement("/SUBJECT/1-items/1", 0)
            containerMessage.insertElement("/SUBJECT/1-items/2", 0)
            cachingPublisher.publish(containerMessage)
          }
        }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
        delay(1)
      }

      test("Generic Container - Upsert and remove quicker than debounce") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record { pattern(pattern = "/SUBJECT/**", supplier = genericContainerFunction) }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))
        delay(50)

        sharedFlow.emitUpdate(Remove(key = "1"))
        delay(100)

        verify(exactly = 0) { cachingPublisher.publish(any()) }

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))
        delay(50)

        sharedFlow.emitUpdate(Upsert(key = "2", value = mapOf()))
        delay(50)

        sharedFlow.emitUpdate(Remove(key = "1"))
        delay(101)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }
        containerMessages.last().also { containerMessage ->
          verifyOrder {
            containerMessage.addElement("/SUBJECT/1-items/2")
            cachingPublisher.publish(containerMessage)
          }
        }

        dataProviderCaptor.onRequest("/SUBJECT/1-items/2")
        delay(1)
        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1-items/2") }
        genericMessages.last().also { genericMessage ->
          verify { cachingPublisher.publish(genericMessage) }
        }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
        dataProviderCaptor.onDiscard("/SUBJECT/1-items/2")
        delay(1)

        confirmVerified(cachedMessageFactory)
      }

      test("Generic Container - Cleanup on error") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record { pattern(pattern = "/SUBJECT/**", supplier = genericContainerFunction) }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.subscriptionCount.value shouldNotBeEqual 0

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))
        delay(50)

        dataProviderCaptor.onRequest("/SUBJECT/1-items/1")
        delay(1)

        sharedFlow.subscriptionCount.value shouldNotBeEqual 0

        sharedFlow.emit(Completion(IllegalStateException()))
        delay(1)

        sharedFlow.subscriptionCount.value shouldBeEqual 0

        verify { cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1", NotFound) }
        subjectErrorEvents.last().also { subjectErrorEvent ->
          verify { cachingPublisher.publishSubjectErrorEvent(subjectErrorEvent) }
        }

        confirmVerified(cachedMessageFactory)
      }

      test("Generic Container - Cleanup on complete") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record { pattern(pattern = "/SUBJECT/**", supplier = genericContainerFunction) }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.subscriptionCount.value shouldNotBeEqual 0

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))
        delay(50)

        dataProviderCaptor.onRequest("/SUBJECT/1-items/1")
        delay(1)

        sharedFlow.subscriptionCount.value shouldNotBeEqual 0

        sharedFlow.emit(Completion())
        delay(1)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }

        sharedFlow.subscriptionCount.value shouldBeEqual 0

        verify { cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1", NotFound) }
        subjectErrorEvents.last().also { subjectErrorEvent ->
          verify { cachingPublisher.publishSubjectErrorEvent(subjectErrorEvent) }
        }

        confirmVerified(cachedMessageFactory)
      }

      test("Generic Container - Row request without container request") {
        dataSource.bind {
          activeContainer {
            record { pattern(pattern = "/SUBJECT/**", supplier = genericContainerFunction) }
          }
        }

        dataProviderCaptor.onRequest("/SUBJECT/1-items/1")
        delay(1)

        verify { cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1-items/1", NotFound) }
        subjectErrorEvents.last().also { subjectErrorEvent ->
          verify { cachingPublisher.publishSubjectErrorEvent(subjectErrorEvent) }
        }
      }

      test("Generic Container - Invalid row request") {
        dataSource.bind(scope = backgroundScope) {
          activeContainer {
            record { pattern(pattern = "/SUBJECT/**", supplier = genericContainerFunction) }
          }
        }

        containerDataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedContainerSubjects.keys shouldContain "/SUBJECT/1"
        val sharedFlow = requestedContainerSubjects.getValue("/SUBJECT/1")

        sharedFlow.subscriptionCount.value shouldNotBeEqual 0

        sharedFlow.emitUpdate(Upsert(key = "1", value = mapOf()))
        delay(101)

        verify { cachedMessageFactory.createContainerMessage("/SUBJECT/1") }

        dataProviderCaptor.onRequest("/SUBJECT/1-items/2")
        delay(9000)
        verify(exactly = 0) {
          cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1-items/2", NotFound)
        }
        delay(1001)

        verify { cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1-items/2", NotFound) }
        subjectErrorEvents.last().also { subjectErrorEvent ->
          verify { cachingPublisher.publishSubjectErrorEvent(subjectErrorEvent) }
        }

        containerDataProviderCaptor.onDiscard("/SUBJECT/1")
        delay(1)

        confirmVerified(cachedMessageFactory)
      }

      test("Generic Record - Publish and complete") {
        dataSource.bind(scope = backgroundScope) {
          active { record { pattern(pattern = "/SUBJECT/**", supplier = genericSubjectFunction) } }
        }

        requestedGenericSubjects.keys shouldNotContain "/SUBJECT/1"

        dataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedGenericSubjects.keys shouldContain "/SUBJECT/1"

        val sharedFlow = requestedGenericSubjects.getValue("/SUBJECT/1")

        sharedFlow.subscriptionCount.value shouldBeEqual 1

        sharedFlow.emitUpdate(mapOf("1" to "a", "2" to "b"))

        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1") }
        genericMessages.last().also { genericMessage ->
          verifyOrder {
            genericMessage.setField("1", "a")
            genericMessage.setField("2", "b")
            cachingPublisher.publish(genericMessage)
          }
        }

        sharedFlow.emitUpdate(mapOf("1" to "x", "2" to "y"))

        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1") }
        genericMessages.last().also { genericMessage ->
          verifyOrder {
            genericMessage.setField("1", "x")
            genericMessage.setField("2", "y")
            cachingPublisher.publish(genericMessage)
          }
        }

        sharedFlow.emit(Completion())

        verify {
          cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1", NotFound)
          cachingPublisher.publishSubjectErrorEvent(subjectErrorEvents.first())
        }

        sharedFlow.subscriptionCount.value shouldBeEqual 0
      }

      test("Generic Record - Cleanup on error") {
        dataSource.bind(scope = backgroundScope) {
          active { record { pattern(pattern = "/SUBJECT/**", supplier = genericSubjectFunction) } }
        }

        requestedGenericSubjects.keys shouldNotContain "/SUBJECT/1"

        dataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)

        requestedGenericSubjects.keys shouldContain "/SUBJECT/1"

        val sharedFlow = requestedGenericSubjects.getValue("/SUBJECT/1")

        sharedFlow.subscriptionCount.value shouldBeEqual 1

        sharedFlow.emitUpdate(mapOf("1" to "a", "2" to "b"))

        verify { cachedMessageFactory.createGenericMessage("/SUBJECT/1") }
        genericMessages.last().also { genericMessage ->
          verifyOrder {
            genericMessage.setField("1", "a")
            genericMessage.setField("2", "b")
            cachingPublisher.publish(genericMessage)
          }
        }

        sharedFlow.emit(Completion(IllegalStateException()))

        sharedFlow.subscriptionCount.value shouldBeEqual 0

        verify {
          cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1", NotFound)
          cachingPublisher.publishSubjectErrorEvent(subjectErrorEvents.first())
        }
      }

      test("Json Record - Publish and complete") {
        dataSource.bind(scope = backgroundScope) {
          active { json { pattern(pattern = "/SUBJECT/**", supplier = jsonSubjectFunction) } }
        }

        requestedJsonSubjects.keys shouldNotContain "/SUBJECT/1"
        dataProviderCaptor.onRequest("/SUBJECT/1")
        delay(1)
        requestedJsonSubjects.keys shouldContain "/SUBJECT/1"

        val sharedFlow = requestedJsonSubjects.getValue("/SUBJECT/1")

        sharedFlow.subscriptionCount.value shouldBeEqual 1

        val a = object {}
        sharedFlow.emitUpdate(a)

        verify { cachedMessageFactory.createJsonMessage("/SUBJECT/1", a) }
        jsonMessages.size shouldBeEqual 1
        jsonMessages.last().also { jsonMessage ->
          verifyOrder { cachingPublisher.publish(jsonMessage) }
        }

        val b = object {}
        sharedFlow.emitUpdate(b)

        verify { cachedMessageFactory.createJsonMessage("/SUBJECT/1", b) }
        jsonMessages.size shouldBeEqual 2
        jsonMessages.last().also { jsonMessage ->
          verifyOrder { cachingPublisher.publish(jsonMessage) }
        }

        sharedFlow.emit(Completion())

        verify {
          cachedMessageFactory.createSubjectErrorEvent("/SUBJECT/1", NotFound)
          cachingPublisher.publishSubjectErrorEvent(subjectErrorEvents.first())
        }

        sharedFlow.subscriptionCount.value shouldBeEqual 0
      }
    })
