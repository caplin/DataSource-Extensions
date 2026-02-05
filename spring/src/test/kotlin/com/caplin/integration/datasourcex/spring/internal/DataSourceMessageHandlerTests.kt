package com.caplin.integration.datasourcex.spring.internal

import com.caplin.integration.datasourcex.reactive.api.ContainerEvent
import com.caplin.integration.datasourcex.spring.annotations.DataMessageMapping
import com.caplin.integration.datasourcex.spring.internal.DataSourceRequestTypeMessageCondition.RequestType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.messaging.handler.CompositeMessageCondition
import org.springframework.messaging.handler.DestinationPatternsMessageCondition
import org.springframework.messaging.handler.HandlerMethod
import org.springframework.util.AntPathMatcher
import org.springframework.util.SimpleRouteMatcher

internal class DataSourceMessageHandlerTests :
    FunSpec({
      val registry = ReactiveAdapterRegistry.getSharedInstance()
      val handler = TestDataSourceMessageHandler().apply { reactiveAdapterRegistry = registry }

      fun getExtendMapping(
          methodName: String,
      ): DataSourceRequestTypeMessageCondition {
        val method = TestController::class.java.getMethod(methodName)
        val handlerMethod = HandlerMethod(TestController(), method)

        val patternsCondition =
            DestinationPatternsMessageCondition(
                arrayOf("/test"),
                SimpleRouteMatcher(AntPathMatcher()),
            )
        val composite =
            CompositeMessageCondition(
                DataSourceRequestTypeMessageCondition.emptyCondition,
                patternsCondition,
            )

        val extended = handler.exposedExtendMapping(composite, handlerMethod)
        return extended.getCondition(DataSourceRequestTypeMessageCondition::class.java)
      }

      test("should infer CONTAINER_JSON for Flow<ContainerEvent<Any>>") {
        val condition = getExtendMapping("containerJson")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.CONTAINER_JSON
      }

      test(
          "should infer CONTAINER_GENERIC for Flow<ContainerEvent<Map<String, String>>> with RECORD_GENERIC"
      ) {
        val condition = getExtendMapping("containerGeneric")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.CONTAINER_GENERIC
      }

      test(
          "should infer CONTAINER_TYPE1 for Flow<ContainerEvent<Map<String, String>>> with RECORD_TYPE1"
      ) {
        val condition = getExtendMapping("containerType1")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.CONTAINER_TYPE1
      }

      test("should infer JSON for Flow<Any>") {
        val condition = getExtendMapping("jsonUpdating")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.JSON
        requestType.shouldBeInstanceOf<RequestType.Stream.Updating>()
      }

      test("should infer JSON for Any (Static)") {
        val condition = getExtendMapping("jsonStatic")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.JSON
        requestType.shouldBeInstanceOf<RequestType.Stream.Static>()
      }

      test("should infer GENERIC for Flow<Map<String, String>> with RECORD_GENERIC") {
        val condition = getExtendMapping("genericUpdating")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.GENERIC
        requestType.shouldBeInstanceOf<RequestType.Stream.Updating>()
      }

      test("should infer TYPE1 for Flow<Map<String, String>> with RECORD_TYPE1") {
        val condition = getExtendMapping("type1Updating")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.TYPE1
        requestType.shouldBeInstanceOf<RequestType.Stream.Updating>()
      }

      test("should infer MAPPING for Flow<String> with MAPPING") {
        val condition = getExtendMapping("mappingUpdating")
        val requestType = condition.requestTypes.single() as RequestType.Stream
        requestType.type shouldBe RequestType.Stream.ObjectType.MAPPING
        requestType.shouldBeInstanceOf<RequestType.Stream.Updating>()
      }
    })

class TestController {
  @DataMessageMapping("/json") fun containerJson(): Flow<ContainerEvent<Any>> = mockk()

  @DataMessageMapping("/generic", type = DataMessageMapping.Type.RECORD_GENERIC)
  fun containerGeneric(): Flow<ContainerEvent<Map<String, String>>> = mockk()

  @DataMessageMapping("/type1", type = DataMessageMapping.Type.RECORD_TYPE1)
  fun containerType1(): Flow<ContainerEvent<Map<String, String>>> = mockk()

  @DataMessageMapping("/json-up") fun jsonUpdating(): Flow<Any> = mockk()

  @DataMessageMapping("/json-static") fun jsonStatic(): Any = mockk()

  @DataMessageMapping("/generic-up", type = DataMessageMapping.Type.RECORD_GENERIC)
  fun genericUpdating(): Flow<Map<String, String>> = mockk()

  @DataMessageMapping("/type1-up", type = DataMessageMapping.Type.RECORD_TYPE1)
  fun type1Updating(): Flow<Map<String, String>> = mockk()

  @DataMessageMapping("/mapping-up", type = DataMessageMapping.Type.MAPPING)
  fun mappingUpdating(): Flow<String> = mockk()
}

internal class TestDataSourceMessageHandler : DataSourceMessageHandler() {
  fun exposedExtendMapping(
      composite: CompositeMessageCondition,
      handler: HandlerMethod,
  ): CompositeMessageCondition {
    return extendMapping(composite, handler)
  }
}
