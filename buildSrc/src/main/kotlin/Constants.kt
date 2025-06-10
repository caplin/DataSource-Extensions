import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STRING

const val rootPackageName = "com.caplin.integration.datasourcex.reactive"
const val apiPackageName = "$rootPackageName.api"
const val corePackageName = "$rootPackageName.core"

sealed class PublisherType(
    val className: ClassName,
    val packageName: String,
    val requiresProjection: Boolean,
) {
  object Kotlin :
      PublisherType(
          flowClassName,
          "kotlin",
          false,
      )

  object Reactive :
      PublisherType(
          reactiveStreamsPublisherClassName,
          "reactivestreams",
          true,
      )

  object Java :
      PublisherType(
          javaFlowPublisherClassName,
          "java",
          true,
      )
}

val binderClassName = ClassName(corePackageName, "Binder")
val iFlowAdapterClassName = ClassName(corePackageName, "IFlowAdapter")

val broadcastEventClassName = ClassName(apiPackageName, "BroadcastEvent")
val antPatternNamespaceClassName =
    ClassName("com.caplin.integration.datasourcex.util", "AntPatternNamespace")
val containerEventClassName = ClassName(apiPackageName, "ContainerEvent")
val containerEventBulkClassName = ClassName(apiPackageName, "ContainerEvent.Bulk")
val containerEventRowEventUpsertClassName =
    ClassName(apiPackageName, "ContainerEvent.RowEvent.Upsert")
val pathSupplierClassName = ClassName(apiPackageName, "PathSupplier")
val pathVariablesSupplierClassName = ClassName(apiPackageName, "PathVariablesSupplier")
val pathVariablesChannelSupplierClassName =
    ClassName(apiPackageName, "PathVariablesChannelSupplier")
val channelSupplierClassName = ClassName(apiPackageName, "ChannelSupplier")
val configBlockClassName = ClassName(apiPackageName, "ConfigBlock")

val pathParameter = ParameterSpec.builder("path", STRING).build()
val patternParameter = ParameterSpec.builder("pattern", STRING).build()
val antNamespaceParameter = ParameterSpec.builder("namespace", antPatternNamespaceClassName).build()

val mutableStateFlowClassName = ClassName("kotlinx.coroutines.flow", "MutableStateFlow")
val flowClassName = ClassName("kotlinx.coroutines.flow", "Flow")

val reactiveStreamsPublisherClassName = ClassName("org.reactivestreams", "Publisher")
val javaFlowPublisherClassName = ClassName("java.util.concurrent", "Flow").nestedClass("Publisher")

val flowMap = MemberName("kotlinx.coroutines.flow", "map")
val collectionMap = MemberName("kotlin.collections", "map")

val prefixNamespaceClassName = ClassName("com.caplin.datasource.namespace", "PrefixNamespace")
val regexNamespaceClassName = ClassName("com.caplin.datasource.namespace", "RegexNamespace")
