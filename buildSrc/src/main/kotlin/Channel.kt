import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INLINE
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName

object Channel : FunctionProvider {

  override fun invoke(
      binderProperty: PropertySpec,
      adapterProperty: PropertySpec,
      configClassName: ClassName,
      parameterClassName: ClassName,
      parameterTypeName: TypeName,
      publisherType: PublisherType,
  ): Functions {

    val r = TypeVariableName("R", ANY)

    val flowParameter =
        ParameterSpec.builder("flow", flowClassName.parameterizedBy(parameterTypeName)).build()

    val receivedFlowParameter =
        ParameterSpec.builder(
                "flow",
                flowClassName.parameterizedBy(
                    if (parameterTypeName == ANY) r else parameterTypeName,
                ),
            )
            .build()

    ParameterSpec.builder(
            "supplier",
            pathSupplierClassName.parameterizedBy(flowClassName.parameterizedBy(parameterTypeName)),
        )
        .build()

    val receiveType = ParameterSpec("receiveType", Class::class.asClassName().parameterizedBy(r))

    val bindFunction = MemberName(binderClassName, "bindChannel${configClassName.simpleName}")

    val configureParameter =
        ParameterSpec.builder("configure", configBlockClassName.parameterizedBy(configClassName))
            .defaultValue("ConfigBlock {}")
            .build()

    val projectedParameterTypeName =
        if (publisherType.requiresProjection) WildcardTypeName.producerOf(parameterTypeName)
        else parameterTypeName

    val channelSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                channelSupplierClassName.parameterizedBy(
                    publisherType.className.parameterizedBy(
                        if (parameterTypeName == ANY) r else parameterTypeName,
                    ),
                    publisherType.className.parameterizedBy(projectedParameterTypeName),
                ),
            )
            .build()

    val pathVariablesChannelSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                pathVariablesChannelSupplierClassName.parameterizedBy(
                    publisherType.className.parameterizedBy(
                        if (parameterTypeName == ANY) r else parameterTypeName,
                    ),
                    publisherType.className.parameterizedBy(projectedParameterTypeName),
                ),
            )
            .build()

    val byNamespaceFunction =
        FunSpec.builder("namespace")
            .addParameter(antNamespaceParameter)
            .apply {
              if (parameterTypeName == ANY) {
                addTypeVariable(r)
                addParameter(receiveType)
              }
            }
            .addParameter(configureParameter)
            .addParameter(pathVariablesChannelSupplierParameter)
            .addCode(
                """
                            %N.%N(%N, %N,%L) { path, pathVariables, receive -> %N.asFlow(%N(path, pathVariables, %N.asPublisher(receive))) }
                        """
                    .trimIndent(),
                binderProperty,
                bindFunction,
                configureParameter,
                antNamespaceParameter,
                if (parameterTypeName == ANY) CodeBlock.of(" %N,", receiveType)
                else CodeBlock.of(""),
                adapterProperty,
                pathVariablesChannelSupplierParameter,
                adapterProperty,
            )
            .build()

    val byNamespaceReifiedFunction =
        if (parameterTypeName == ANY)
            FunSpec.builder("namespace")
                .addParameter(antNamespaceParameter)
                .apply {
                  if (parameterTypeName == ANY) {
                    addModifiers(INLINE)
                    addTypeVariable(r.copy(reified = true))
                  }
                }
                .addParameter(configureParameter)
                .addParameter(pathVariablesChannelSupplierParameter)
                .addCode(
                    """
                            %N(%N, %T::class.java, %N, %N)
                        """
                        .trimIndent(),
                    byNamespaceFunction,
                    antNamespaceParameter,
                    r,
                    configureParameter,
                    pathVariablesChannelSupplierParameter,
                )
                .build()
        else null

    val byPatternFunction =
        FunSpec.builder("pattern")
            .addParameter(patternParameter)
            .apply {
              if (parameterTypeName == ANY) {
                addTypeVariable(r)
                addParameter(receiveType)
              }
            }
            .addParameter(configureParameter)
            .addParameter(pathVariablesChannelSupplierParameter)
            .addCode(
                """
                            val namespace = %T(%N)
                            %N(namespace,%L %N, %N)
                        """
                    .trimIndent(),
                antPatternNamespaceClassName,
                patternParameter,
                byNamespaceFunction,
                if (parameterTypeName == ANY) CodeBlock.of(" %N,", receiveType)
                else CodeBlock.of(""),
                configureParameter,
                pathVariablesChannelSupplierParameter,
            )
            .build()

    val byPatternReifiedFunction =
        if (parameterTypeName == ANY)
            FunSpec.builder("pattern")
                .addParameter(patternParameter)
                .apply {
                  if (parameterTypeName == ANY) {
                    addModifiers(INLINE)
                    addTypeVariable(r.copy(reified = true))
                  }
                }
                .addParameter(configureParameter)
                .addParameter(pathVariablesChannelSupplierParameter)
                .addCode(
                    """
                            %N(%N, %T::class.java, %N, %N)
                        """
                        .trimIndent(),
                    byPatternFunction,
                    patternParameter,
                    r,
                    configureParameter,
                    pathVariablesChannelSupplierParameter,
                )
                .build()
        else null

    val flowSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                LambdaTypeName.get(null, listOf(receivedFlowParameter), flowParameter.type),
            )
            .build()

    val byPathFunction =
        FunSpec.builder("path")
            .addParameter(pathParameter)
            .apply {
              if (parameterTypeName == ANY) {
                addTypeVariable(r)
                addParameter(receiveType)
              }
            }
            .addParameter(configureParameter)
            .addParameter(channelSupplierParameter)
            .addCode(
                """
                            val namespace = %T(%N)
                            check(namespace.isExact) { %S }
                            %N(namespace,%L %N) { _, _, %N -> %N(%N) }
                        """
                    .trimIndent(),
                antPatternNamespaceClassName,
                pathParameter,
                "Requires exact path without wildcards or path variables, consider using ${byPatternFunction.name}",
                byNamespaceFunction,
                if (parameterTypeName == ANY) CodeBlock.of(" %N,", receiveType)
                else CodeBlock.of(""),
                configureParameter,
                receivedFlowParameter,
                channelSupplierParameter,
                receivedFlowParameter,
            )
            .build()

    val byPathReifiedFunction =
        if (parameterTypeName == ANY)
            FunSpec.builder("path")
                .addParameter(pathParameter)
                .apply {
                  if (parameterTypeName == ANY) {
                    addModifiers(INLINE)
                    addTypeVariable(r.copy(reified = true))
                  }
                }
                .addParameter(configureParameter)
                .addParameter(channelSupplierParameter)
                .addCode(
                    """
                            %N(%N, %T::class.java, %N, %N)
                        """
                        .trimIndent(),
                    byPathFunction,
                    pathParameter,
                    r,
                    configureParameter,
                    flowSupplierParameter,
                )
                .build()
        else null

    return Functions(
        listOfNotNull(
            byNamespaceFunction,
            byNamespaceReifiedFunction,
            byPatternFunction,
            byPatternReifiedFunction,
            byPathFunction,
            byPathReifiedFunction,
        ),
    )
  }
}
