package com.caplin.integration.datasourcex.spring.internal

import com.caplin.integration.datasourcex.spring.internal.DataSourceRequestTypeMessageCondition.RequestType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class DataSourceRequestTypeMessageConditionTests :
    FunSpec({
      test("should support container object types") {
        val jsonCondition =
            DataSourceRequestTypeMessageCondition.streamStaticCondition(
                RequestType.Stream.ObjectType.CONTAINER_JSON
            )
        val type1Condition =
            DataSourceRequestTypeMessageCondition.streamStaticCondition(
                RequestType.Stream.ObjectType.CONTAINER_TYPE1
            )
        val genericCondition =
            DataSourceRequestTypeMessageCondition.streamStaticCondition(
                RequestType.Stream.ObjectType.CONTAINER_GENERIC
            )

        jsonCondition.requestTypes.single().let {
          it as RequestType.Stream
          it.type shouldBe RequestType.Stream.ObjectType.CONTAINER_JSON
        }

        type1Condition.requestTypes.single().let {
          it as RequestType.Stream
          it.type shouldBe RequestType.Stream.ObjectType.CONTAINER_TYPE1
        }

        genericCondition.requestTypes.single().let {
          it as RequestType.Stream
          it.type shouldBe RequestType.Stream.ObjectType.CONTAINER_GENERIC
        }
      }

      test("should combine conditions with container types") {
        val jsonCondition =
            DataSourceRequestTypeMessageCondition.streamStaticCondition(
                RequestType.Stream.ObjectType.CONTAINER_JSON
            )
        val genericCondition =
            DataSourceRequestTypeMessageCondition.streamStaticCondition(
                RequestType.Stream.ObjectType.CONTAINER_GENERIC
            )

        val combined = jsonCondition.combine(genericCondition)

        combined.requestTypes.size shouldBe 2
        combined.requestTypes.map { (it as RequestType.Stream).type } shouldContain
            RequestType.Stream.ObjectType.CONTAINER_JSON

        combined.requestTypes.map { (it as RequestType.Stream).type } shouldContain
            RequestType.Stream.ObjectType.CONTAINER_GENERIC
      }
    })
