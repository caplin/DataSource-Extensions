import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName

object Broadcast : FunctionProvider {

  override fun invoke(
      binderProperty: PropertySpec,
      adapterProperty: PropertySpec,
      configClassName: ClassName,
      parameterClassName: ClassName,
      parameterTypeName: TypeName,
      publisherType: PublisherType,
  ): Functions {

    val broadcastEvent = broadcastEventClassName.parameterizedBy(parameterTypeName)

    val bindFunction = MemberName(binderClassName, "bindBroadcast${configClassName.simpleName}")

    val valueParameter = ParameterSpec.builder("staticValue", parameterTypeName).build()

    val configureParameter =
        ParameterSpec.builder("configure", configBlockClassName.parameterizedBy(configClassName))
            .defaultValue("ConfigBlock {}")
            .build()

    val broadcastEventPublisherParameter =
        ParameterSpec.builder(
                "publisher",
                publisherType.className.parameterizedBy(broadcastEvent),
            )
            .build()

    val projectedParameterTypeName =
        if (publisherType.requiresProjection) WildcardTypeName.producerOf(parameterTypeName)
        else parameterTypeName

    val publisherParameter =
        ParameterSpec.builder(
                "publisher",
                publisherType.className.parameterizedBy(projectedParameterTypeName),
            )
            .build()

    val byNamespaceFunction =
        FunSpec.builder("namespace")
            .addAnnotation(JvmOverloads::class)
            .addParameter(antNamespaceParameter)
            .addParameter(broadcastEventPublisherParameter)
            .addParameter(configureParameter)
            .addCode(
                """
                        %N.%N(%N, %N, %N.asFlow(%N))
                    """
                    .trimIndent(),
                binderProperty,
                bindFunction,
                configureParameter,
                antNamespaceParameter,
                adapterProperty,
                broadcastEventPublisherParameter,
            )
            .build()

    val byPatternFunction =
        FunSpec.builder("pattern")
            .addAnnotation(JvmOverloads::class)
            .addParameter(patternParameter)
            .addParameter(broadcastEventPublisherParameter)
            .addParameter(configureParameter)
            .addCode(
                """
                        %N(%T(%N), %N, %N)
                    """
                    .trimIndent(),
                byNamespaceFunction,
                antPatternNamespaceClassName,
                patternParameter,
                broadcastEventPublisherParameter,
                configureParameter,
            )
            .build()

    val byPathFunction =
        FunSpec.builder("path")
            .addAnnotation(JvmOverloads::class)
            .addParameter(pathParameter)
            .addParameter(publisherParameter)
            .addParameter(configureParameter)
            .addCode(
                """
                        val namespace = %T(%N)
                        check(namespace.isExact) { %S }
                        %N(namespace, %N.asPublisher(%N.asFlow<%T>(%N).%M { %T(%N, it) }), %N)
                    """
                    .trimIndent(),
                antPatternNamespaceClassName,
                pathParameter,
                "Requires exact path without wildcards or path variables, consider using ${byPatternFunction.name}",
                byNamespaceFunction,
                adapterProperty,
                adapterProperty,
                parameterTypeName,
                publisherParameter,
                flowMap,
                broadcastEventClassName,
                pathParameter,
                configureParameter,
            )
            .build()
    val byPathValueFunction =
        FunSpec.builder("path")
            .addAnnotation(JvmOverloads::class)
            .addParameter(pathParameter)
            .addParameter(valueParameter)
            .addParameter(configureParameter)
            .addCode(
                """
                        val publisher: %T = %N.asPublisher(%T(%N))
                        %N(%N, publisher, %N)
                    """
                    .trimIndent(),
                publisherParameter.type,
                adapterProperty,
                mutableStateFlowClassName,
                valueParameter,
                byPathFunction,
                pathParameter,
                configureParameter,
            )
            .build()

    return Functions(
        listOf(
            byNamespaceFunction,
            byPatternFunction,
            byPathFunction,
            byPathValueFunction,
        ),
    )
  }
}
