package com.caplin.integration.kotest

import com.caplin.integration.kotest.LiberatorContainerExtension.Config
import com.caplin.integration.kotest.LiberatorContainerExtension.Liberator
import com.caplin.integration.streamlinkx.StreamLinkConnection
import com.caplin.integration.streamlinkx.StreamLinkConnectionFactory
import com.caplin.integration.streamlinkx.StreamLinkConnectionFactory.Companion.convertDerToPem
import com.caplin.keymaster.IKeyMasterConfiguration
import com.caplin.keymaster.KeyMasterHashingAlgorithm.SHA256
import com.caplin.keymaster.PEMPKCS8KeyMasterConfiguration
import io.kotest.core.extensions.MountableExtension
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.TestListener
import java.io.InputStream
import java.security.KeyPairGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.testcontainers.containers.ContainerState
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.images.builder.Transferable
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * A Kotest [MountableExtension] that runs a Caplin Liberator inside a Testcontainers container for
 * the duration of a test project.
 *
 * Mount it on a spec to obtain a [Liberator] handle, which exposes the mapped ports and a factory
 * for opening authenticated StreamLink connections. The container is started lazily on first
 * [mount] and stopped once via [afterProject] when the project completes.
 *
 * ```
 * class MySpec : FunSpec({
 *   val liberator = install(LiberatorContainerExtension())
 *   test("...") {
 *     val connection = liberator.connect("alice")
 *     autoClose(connection)
 *     // ...
 *   }
 * })
 * ```
 *
 * @param containerConfig how the container is built and how clients authenticate against it.
 *   Defaults to a [DefaultContainerConfig] that builds a Liberator image with a generated KeyMaster
 *   key pair. Supply a [CustomContainerConfig] to run against a pre-built container.
 * @param objectMapper Jackson mapper used when (de)serialising messages over the connection.
 */
class LiberatorContainerExtension(
    private val containerConfig: ContainerConfig = DefaultContainerConfig(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : MountableExtension<Config, Liberator>, AfterProjectListener, TestListener {

  private companion object {
    const val LIBERATOR_HTTP_PORT = 18080
    const val LIBERATOR_DATA_SOURCE_PORT = 19000
    const val DEFAULT_BASE_IMAGE = "docker-release.caplin.com/platform/core:8.0.19"
  }

  /**
   * Describes the container to run and how StreamLink clients authenticate against it.
   *
   * Implemented by [DefaultContainerConfig] (builds a Liberator image on the fly) and
   * [CustomContainerConfig] (wraps a container and configuration you supply).
   */
  sealed interface ContainerConfig {
    /** KeyMaster configuration used to sign the credentials of connecting clients. */
    val keymasterConfiguration: IKeyMasterConfiguration
    /** The container to start; its exposed ports are mapped to random host ports. */
    val container: GenericContainer<*>
    /** The container port serving the Liberator HTTP/RTTP endpoint. */
    val httpPort: Int
    /** The container port serving the DataSource endpoint. */
    val dataSourcePort: Int
  }

  /**
   * A [ContainerConfig] that builds a Liberator image from the bundled Dockerfile and generates a
   * fresh RSA KeyMaster key pair, deploying the public key into the container.
   *
   * @param adapterConfig optional adapter configuration copied to `/app/adapter.conf`.
   * @param beforeScript optional script copied to `/app/beforeScript.sh` and run before startup.
   * @param extraFiles additional files to copy into the container, keyed by destination path.
   * @param baseImage the base Docker image the Liberator image is built from.
   */
  data class DefaultContainerConfig(
      val adapterConfig: InputStream? = null,
      val beforeScript: InputStream? = null,
      val extraFiles: Map<String, InputStream> = mutableMapOf(),
      val baseImage: String = DEFAULT_BASE_IMAGE,
  ) : ContainerConfig {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.genKeyPair()

    override val keymasterConfiguration: IKeyMasterConfiguration =
        PEMPKCS8KeyMasterConfiguration(
            keys.private.encoded.convertDerToPem().byteInputStream(),
            SHA256,
            null,
        )

    override val container: GenericContainer<*> =
        GenericContainer(
                ImageFromDockerfile()
                    .withFileFromClasspath("liberator.sh", "default-liberator/liberator.sh")
                    .withFileFromClasspath("Dockerfile", "default-liberator/Dockerfile")
                    .withBuildArg("BASE_IMAGE", baseImage),
            )
            .withExposedPorts(
                LIBERATOR_HTTP_PORT,
                LIBERATOR_DATA_SOURCE_PORT,
            )
            .withCopyToContainer(
                Transferable.of(keys.public.encoded),
                "/app/DeploymentFramework/global_config/ssl/keymaster_public.der",
            )
    override val httpPort = LIBERATOR_HTTP_PORT
    override val dataSourcePort = LIBERATOR_DATA_SOURCE_PORT
  }

  /**
   * A [ContainerConfig] for running against a container and KeyMaster configuration you supply
   * yourself, rather than the one built by [DefaultContainerConfig].
   */
  data class CustomContainerConfig(
      override val keymasterConfiguration: IKeyMasterConfiguration,
      override val httpPort: Int,
      override val dataSourcePort: Int,
      override val container: GenericContainer<*>,
  ) : ContainerConfig

  /** Configuration receiver passed to [mount]; currently exposes no options. */
  class Config

  /**
   * A handle to a running Liberator container, returned when the extension is mounted.
   *
   * @property streamLinkConnectionFactory factory for opening StreamLink connections to the
   *   container, pre-configured with the host, port and KeyMaster credentials.
   * @property dataSourcePort the host port mapped to the container's DataSource port.
   * @property httpPort the host port mapped to the container's HTTP/RTTP port.
   * @property containerState the underlying Testcontainers container state.
   */
  data class Liberator(
      val streamLinkConnectionFactory: StreamLinkConnectionFactory,
      val dataSourcePort: Int,
      val httpPort: Int,
      val containerState: ContainerState,
  ) {
    /** Opens a StreamLink connection authenticated as [username]. */
    fun connect(username: String): StreamLinkConnection =
        streamLinkConnectionFactory.connect(username)
  }

  private val container: GenericContainer<*> = containerConfig.container

  private val liberator by lazy {
    Liberator(
        StreamLinkConnectionFactory(
            "rttp://${container.host}:${container.getMappedPort(containerConfig.httpPort)}",
            containerConfig.keymasterConfiguration,
            objectMapper,
        ),
        container.getMappedPort(containerConfig.dataSourcePort),
        container.getMappedPort(containerConfig.httpPort),
        container,
    )
  }

  /**
   * Starts the container (if not already running) and returns a [Liberator] handle.
   *
   * For a [DefaultContainerConfig], any configured extra files, before-script and adapter config
   * are copied into the container before it is started. Subsequent calls return the same handle
   * without restarting the container.
   */
  override fun mount(configure: Config.() -> Unit): Liberator {

    if (container.containerId != null) return liberator

    val config = Config()
    config.configure()

    when (val containerConfig = containerConfig) {
      is CustomContainerConfig -> {}

      is DefaultContainerConfig -> {
        containerConfig.extraFiles.forEach { (path, stream) ->
          container.withCopyToContainer(Transferable.of(stream.readBytes()), path)
        }

        containerConfig.beforeScript?.let {
          container.withCopyToContainer(
              Transferable.of(it.readBytes()),
              "/app/beforeScript.sh",
          )
        }

        containerConfig.adapterConfig?.let {
          container.withCopyToContainer(
              Transferable.of(it.readBytes()),
              "/app/adapter.conf",
          )
        }
      }
    }

    container.start()
    return liberator
  }

  /** Stops the container once the test project has finished. */
  override suspend fun afterProject() {
    withContext(Dispatchers.IO) { container.stop() }
  }
}
