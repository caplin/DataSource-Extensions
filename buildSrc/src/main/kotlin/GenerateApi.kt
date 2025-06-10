import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.util.function.Consumer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

private data class PublishType(
    val name: String,
    val messageTypes: List<MessageType>,
    val functionProvider: FunctionProvider,
)

private data class MessageType(
    val name: String,
    val doc: String,
    val sendClassName: ClassName,
    val sendTypeName: TypeName,
)

private val mappingMessageType =
    MessageType(
        name = "Mapping",
        doc =
            "[mapping](https://www.caplin.com/developer/caplin-platform/platform-architecture/mapping) from one subject to another",
        sendClassName = STRING,
        sendTypeName = STRING,
    )

private val jsonMessageType =
    MessageType(
        name = "Json",
        doc =
            "[JSON](https://www.caplin.com/developer/caplin-platform/datasource/datasource-json-data) records",
        sendClassName = ANY,
        sendTypeName = ANY,
    )

private val recordMessageType =
    MessageType(
        name = "Record",
        doc =
            "[Generic](https://www.caplin.com/developer/caplin-platform/datasource/datasource-generic-data) or " +
                "[Type1](https://www.caplin.com/developer/caplin-platform/datasource/datasource-type-1-data) records",
        sendClassName = MAP,
        sendTypeName = MAP.parameterizedBy(STRING, STRING),
    )

private val activePublishType =
    PublishType(
        name = "Active",
        messageTypes =
            listOf(
                mappingMessageType,
                jsonMessageType,
                recordMessageType,
            ),
        functionProvider = Active,
    )

private val activeContainerPublishType =
    PublishType(
        name = "ActiveContainer",
        messageTypes =
            listOf(
                jsonMessageType,
                recordMessageType,
            ),
        functionProvider = ActiveContainer,
    )

private val channelPublishType =
    PublishType(
        name = "Channel",
        messageTypes =
            listOf(
                jsonMessageType,
                recordMessageType,
            ),
        functionProvider = Channel,
    )

private val broadcastPublishType =
    PublishType(
        name = "Broadcast",
        messageTypes =
            listOf(
                mappingMessageType,
                recordMessageType,
            ),
        functionProvider = Broadcast,
    )

@CacheableTask
abstract class GenerateApi : DefaultTask() {

  @get:Input abstract val publisherType: Property<String>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    arrayOf(
            activePublishType,
            activeContainerPublishType,
            channelPublishType,
            broadcastPublishType,
        )
        .forEach { (publishTypeName, messageTypes, functionFactory) ->
          val bindTypeName = "Bind$publishTypeName"

          val binderProperty =
              "binder"
                  .let { propertyName ->
                    PropertySpec.builder(propertyName, binderClassName, PRIVATE)
                        .initializer(propertyName)
                        .build()
                  }

          val adapterProperty =
              "adapter"
                  .let { propertyName ->
                    PropertySpec.builder(
                            propertyName,
                            iFlowAdapterClassName,
                            PRIVATE,
                        )
                        .initializer(propertyName)
                        .build()
                  }

          val publisherType =
              PublisherType::class
                  .sealedSubclasses
                  .map { it.objectInstance!! }
                  .single { it.packageName == publisherType.get() }

          val binderConstructorFunction =
              FunSpec.constructorBuilder()
                  .addParameter(binderProperty.name, binderProperty.type)
                  .addParameter(adapterProperty.name, adapterProperty.type)
                  .addModifiers(INTERNAL)
                  .build()

          val packageName = "$rootPackageName.${publisherType.packageName}"
          val fileBuilder = FileSpec.builder(packageName, bindTypeName)

          val classBuilder =
              TypeSpec.classBuilder(ClassName(packageName, bindTypeName))
                  .addProperty(binderProperty)
                  .addProperty(adapterProperty)
                  .primaryConstructor(binderConstructorFunction)

          messageTypes.forEach { messageType ->
            val (messageTypeName, doc, parameterClassName, parameterTypeName) = messageType

            val messageTypeLowercaseName = messageTypeName.lowercase()

            val configClassName =
                ClassName(apiPackageName, "${publishTypeName}Config").nestedClass(messageTypeName)

            val (javaFunctions) =
                functionFactory(
                    binderProperty,
                    "adapter"
                        .let { propertyName -> PropertySpec.builder(propertyName, ANY).build() },
                    configClassName,
                    parameterClassName,
                    parameterTypeName,
                    publisherType,
                )

            // Create the Java classes and methods which delegate to the Flow implementations

            val messageClassName =
                ClassName(
                    "$rootPackageName.${publisherType.packageName}",
                    "$bindTypeName$messageTypeName",
                )

            fileBuilder.addType(
                TypeSpec.classBuilder(messageClassName)
                    .addProperty(binderProperty)
                    .addProperty(adapterProperty)
                    .primaryConstructor(binderConstructorFunction)
                    .apply { javaFunctions.forEach { function -> addFunction(function) } }
                    .build(),
            )

            val messageTypeConsumerClassName =
                Consumer::class.asClassName().parameterizedBy(messageClassName)

            val messageTypeConsumerParameter1 =
                ParameterSpec.builder(
                        messageTypeLowercaseName,
                        LambdaTypeName.get(messageClassName, returnType = UNIT),
                    )
                    .build()

            val kotlinReceiverFunction =
                FunSpec.builder(messageTypeLowercaseName)
                    .addAnnotation(JvmSynthetic::class)
                    .addKdoc("Configure bindings for publishers providing $doc.")
                    .addParameter(messageTypeConsumerParameter1)
                    .addCode(
                        "%T(%N, %N).also(%N)",
                        messageClassName,
                        binderProperty,
                        adapterProperty,
                        messageTypeConsumerParameter1,
                    )
                    .build()

            classBuilder.addFunction(kotlinReceiverFunction)

            val messageTypeConsumerParameter =
                ParameterSpec.builder(messageTypeLowercaseName, messageTypeConsumerClassName)
                    .build()

            val function =
                FunSpec.builder(messageTypeLowercaseName)
                    .addKdoc("Configure bindings for publishers providing $doc.")
                    .addParameter(messageTypeConsumerParameter)
                    .addCode(
                        "%N(%N::accept)",
                        kotlinReceiverFunction,
                        messageTypeConsumerParameter,
                    )
                    .build()

            classBuilder.addFunction(function)
          }

          fileBuilder.addType(classBuilder.build()).build().writeTo(outputDirectory.get().asFile)
        }
  }
}
