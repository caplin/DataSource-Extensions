import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName

object Active : FunctionProvider {

  override fun invoke(
      binderProperty: PropertySpec,
      adapterProperty: PropertySpec,
      configClassName: ClassName,
      parameterClassName: ClassName,
      parameterTypeName: TypeName,
      publisherType: PublisherType,
  ): Functions {

    val bindFunction = MemberName(binderClassName, "bindActive${configClassName.simpleName}")

    val configureParameter =
        ParameterSpec.builder("configure", configBlockClassName.parameterizedBy(configClassName))
            .defaultValue("ConfigBlock {}")
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

    val publisherPatternSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                requestSupplierClassName.parameterizedBy(
                    publisherType.className.parameterizedBy(projectedParameterTypeName),
                ),
            )
            .build()

    val byNamespaceFunction =
        FunSpec.builder("namespace")
            .addKdoc(
                createNamespaceDoc(
                    publisherPatternSupplierParameter,
                    configureParameter,
                    publisherType.className,
                ),
            )
            .addAnnotation(JvmOverloads::class)
            .addParameter(antNamespaceParameter)
            .addParameter(configureParameter)
            .addParameter(publisherPatternSupplierParameter)
            .addCode(
                """
                        %N.%N(%N, %N) { %N.asFlow(with(%N) { %T(it, %N.extractPathVariables(it), %N.extractQueryParameters(it)).invoke() }) }
                    """
                    .trimIndent(),
                binderProperty,
                bindFunction,
                configureParameter,
                antNamespaceParameter,
                adapterProperty,
                publisherPatternSupplierParameter,
                requestClassName,
                antNamespaceParameter,
                antNamespaceParameter,
            )
            .build()

    val byPatternFunction =
        FunSpec.builder("pattern")
            .addKdoc(
                createPatternDoc(
                    publisherPatternSupplierParameter,
                    configureParameter,
                    publisherType.className,
                ),
            )
            .addAnnotation(JvmOverloads::class)
            .addParameter(patternParameter)
            .addParameter(configureParameter)
            .addParameter(publisherPatternSupplierParameter)
            .addCode(
                """
                        val namespace = %T(%N)
                        %N(namespace, %N, %N)
                    """
                    .trimIndent(),
                antPatternNamespaceClassName,
                patternParameter,
                byNamespaceFunction,
                configureParameter,
                publisherPatternSupplierParameter,
            )
            .build()

    val deprecatedSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                pathVariablesSupplierClassName.parameterizedBy(
                    publisherType.className.parameterizedBy(projectedParameterTypeName),
                ),
            )
            .build()

    val deprecatedAnnotation =
        AnnotationSpec.builder(Deprecated::class)
            .addMember(
                "%S",
                "Use the supplier overload taking a Request, which also carries query parameters.",
            )
            .build()

    val suppressDeprecationAnnotation =
        AnnotationSpec.builder(Suppress::class).addMember("%S", "DEPRECATION").build()

    val byNamespaceDeprecatedFunction =
        FunSpec.builder("namespace")
            .addAnnotation(deprecatedAnnotation)
            .addAnnotation(suppressDeprecationAnnotation)
            .addAnnotation(JvmOverloads::class)
            .addParameter(antNamespaceParameter)
            .addParameter(configureParameter)
            .addParameter(deprecatedSupplierParameter)
            .addCode(
                "%N(%N, %N, %T { %N(path, pathVariables) })",
                byNamespaceFunction,
                antNamespaceParameter,
                configureParameter,
                requestSupplierClassName,
                deprecatedSupplierParameter,
            )
            .build()

    val byPatternDeprecatedFunction =
        FunSpec.builder("pattern")
            .addAnnotation(deprecatedAnnotation)
            .addAnnotation(suppressDeprecationAnnotation)
            .addAnnotation(JvmOverloads::class)
            .addParameter(patternParameter)
            .addParameter(configureParameter)
            .addParameter(deprecatedSupplierParameter)
            .addCode(
                """
                        val namespace = %T(%N)
                        %N(namespace, %N, %N)
                    """
                    .trimIndent(),
                antPatternNamespaceClassName,
                patternParameter,
                byNamespaceDeprecatedFunction,
                configureParameter,
                deprecatedSupplierParameter,
            )
            .build()

    val byPathFunction =
        FunSpec.builder("path")
            .addAnnotation(JvmOverloads::class)
            .addKdoc(createPathDoc(publisherParameter, configureParameter))
            .addParameter(pathParameter)
            .addParameter(publisherParameter)
            .addParameter(configureParameter)
            .addCode(
                """
                        val namespace = %T(%N)
                        check(namespace.isExact) { %S }
                        %N(namespace, %N) { %N }
                    """
                    .trimIndent(),
                antPatternNamespaceClassName,
                pathParameter,
                "Requires exact path without wildcards or path variables, consider using ${byPatternFunction.name}",
                byNamespaceFunction,
                configureParameter,
                publisherParameter,
            )
            .build()

    val valueParameter = ParameterSpec.builder("staticValue", parameterTypeName).build()
    val byPathValueFunction =
        FunSpec.builder("path")
            .addAnnotation(JvmOverloads::class)
            .addKdoc(createPathValueDoc(valueParameter, configureParameter))
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
            byNamespaceDeprecatedFunction,
            byPatternFunction,
            byPatternDeprecatedFunction,
            byPathFunction,
            byPathValueFunction,
        ),
    )
  }

  private fun createNamespaceDoc(
      flowSupplierParameter: ParameterSpec,
      configureParameter: ParameterSpec,
      publisherType: ClassName,
  ) =
      CodeBlock.builder()
          .add(
              """
        Bind a [%N] to a [%N] of data publishers.
        
        For example, if a [%T] for /fx/ is bound then when a request for /fx/gbpusd is received
        the [%N] will be called with a [%T] whose path is /fx/gbpusd to return a [%T] which will be subscribed to
        in order to provide data.

        @param %N A namespace for matching subjects for which this [%N] will be invoked.
        @param %N A configuration block that can be used to set up the behaviour of this publisher.
        @param %N This will be invoked on each incoming request. It should parse the `subject` and provide a
        %T capable of supplying data in response.

        @see %T
        @see %T
        @see %T
    """
                  .trimIndent(),
              antNamespaceParameter,
              flowSupplierParameter,
              prefixNamespaceClassName,
              flowSupplierParameter,
              requestClassName,
              publisherType,
              antNamespaceParameter,
              flowSupplierParameter,
              configureParameter,
              flowSupplierParameter,
              publisherType,
              regexNamespaceClassName,
              prefixNamespaceClassName,
              antPatternNamespaceClassName,
          )
          .build()

  private fun createPatternDoc(
      flowSupplierParameter: ParameterSpec,
      configureParameter: ParameterSpec,
      publisherType: ClassName,
  ) =
      CodeBlock.builder()
          .add(
              """
        Bind an ant pattern to a [%N] of data publishers.
         
        For example, if [%N] is provided as /fx&#47;* then when a request for /fx/gbpusd is received
        the [%N] will be called with a [%T] whose path is /fx/gbpusd to return a [%T] which will be subscribed to
        in order to provide data.

        @param %N The ant pattern for matching subjects for which this [%N] will be invoked.
        @param %N A configuration block that can be used to set up the behaviour of this publisher.
        @param %N This will be invoked on each incoming request. It should parse the `subject` and provide a
        %T capable of supplying data in response.

        @see %T
    """
                  .trimIndent(),
              flowSupplierParameter,
              patternParameter,
              flowSupplierParameter,
              requestClassName,
              publisherType,
              patternParameter,
              flowSupplierParameter,
              configureParameter,
              flowSupplierParameter,
              publisherType,
              antPatternNamespaceClassName,
          )
          .build()

  private fun createPathDoc(
      publisherParameter: ParameterSpec,
      configureParameter: ParameterSpec,
  ) =
      CodeBlock.builder()
          .add(
              """
        Bind an ant path to a data publisher.
         
        For example, if [%N] is provided as /fx/gbpusd then when a request for /fx/gbpusd is received
        [%N] will be subscribed to in order to provide data.
         
        @param %N The ant path for which [%N] will be subscribed to.
        @param %N A configuration block that can be used to set up the behaviour of this publisher.
        @param %N This will be subscribed to and should be capable of supplying data in response.
        
        @see %T
    """
                  .trimIndent(),
              pathParameter,
              publisherParameter,
              pathParameter,
              publisherParameter,
              configureParameter,
              publisherParameter,
              antPatternNamespaceClassName,
          )
          .build()

  private fun createPathValueDoc(
      dataParameter: ParameterSpec,
      configureParameter: ParameterSpec,
  ) =
      CodeBlock.builder()
          .add(
              """
        Bind an ant path to a specific value.
         
        For example, if [%N] is provided as /fx/gbpusd then when a request for /fx/gbpusd is received
        [%N] will be returned in response.
         
        @param %N The ant path for which [%N] will be returned.
        @param %N A configuration block that can be used to set up the behaviour of this publisher.
        @param %N The data that will be returned in response.
        
        @see %T
    """
                  .trimIndent(),
              pathParameter,
              dataParameter,
              pathParameter,
              dataParameter,
              configureParameter,
              dataParameter,
              antPatternNamespaceClassName,
          )
          .build()
}
