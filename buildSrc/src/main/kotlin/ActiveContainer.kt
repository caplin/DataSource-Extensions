import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName

object ActiveContainer : FunctionProvider {

  override fun invoke(
      binderProperty: PropertySpec,
      adapterProperty: PropertySpec,
      configClassName: ClassName,
      parameterClassName: ClassName,
      parameterTypeName: TypeName,
      publisherType: PublisherType,
  ): Functions {

    val containerEventTypeName = containerEventClassName.parameterizedBy(parameterTypeName)

    val bindFunction =
        MemberName(binderClassName, "bindActiveContainer${configClassName.simpleName}")

    val valueParameter =
        ParameterSpec.builder(
                "staticValue",
                Map::class.asClassName().parameterizedBy(STRING, parameterTypeName),
            )
            .build()

    val configureParameter =
        ParameterSpec.builder("configure", configBlockClassName.parameterizedBy(configClassName))
            .defaultValue("ConfigBlock {}")
            .build()

    val publisherParameter =
        ParameterSpec.builder(
                "publisher",
                publisherType.className.parameterizedBy(containerEventTypeName),
            )
            .build()

    val publisherPatternSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                requestSupplierClassName.parameterizedBy(
                    publisherType.className.parameterizedBy(containerEventTypeName),
                ),
            )
            .build()

    val primaryFunction =
        FunSpec.builder("namespace")
            .addAnnotation(JvmOverloads::class)
            .addParameter(antNamespaceParameter)
            .addParameter(configureParameter)
            .addParameter(publisherPatternSupplierParameter)
            .addCode(
                """
                        %N.%N(%N, %N) { path -> %N.asFlow(with(%N) { %T(path, %N.extractPathVariables(path), %N.extractQueryParameters(path)).invoke() }) }
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
                primaryFunction,
                configureParameter,
                publisherPatternSupplierParameter,
            )
            .build()

    val deprecatedSupplierParameter =
        ParameterSpec.builder(
                "supplier",
                pathVariablesSupplierClassName.parameterizedBy(
                    publisherType.className.parameterizedBy(containerEventTypeName),
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
                primaryFunction,
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
                "Requires exact path without wildcards or path variables, consider using ${primaryFunction.name}",
                primaryFunction,
                configureParameter,
                publisherParameter,
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
                        val publisher: %T = %N.asPublisher(%T(%T(%N.%M { %T(it.key, it.value) })))
                        %N(%N, publisher, %N)
                    """
                    .trimIndent(),
                publisherParameter.type,
                adapterProperty,
                mutableStateFlowClassName,
                containerEventBulkClassName,
                valueParameter,
                collectionMap,
                containerEventRowEventUpsertClassName,
                byPathFunction,
                pathParameter,
                configureParameter,
            )
            .build()

    return Functions(
        listOf(
            primaryFunction,
            byNamespaceDeprecatedFunction,
            byPatternFunction,
            byPatternDeprecatedFunction,
            byPathFunction,
            byPathValueFunction,
        ),
    )
  }
}
