dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven {
      url = uri("https://artifactory.caplin.com/artifactory/caplin-release")
      credentials {
        val artifactoryUsername: String? by settings
        val artifactoryPassword: String? by settings
        username =
            checkNotNull(
                artifactoryUsername ?: System.getenv("ARTIFACTORY_USERNAME"),
            ) {
              "Missing artifactoryUsername property or ARTIFACTORY_USERNAME environment variable"
            }
        password =
            checkNotNull(
                artifactoryPassword ?: System.getenv("ARTIFACTORY_PASSWORD"),
            ) {
              "Missing artifactoryPassword property or ARTIFACTORY_PASSWORD environment variable"
            }
      }
    }
  }
}

rootProject.name = "datasourcex"

include("util")

project(":util").name = "datasourcex-util"

include("reactive:core")

project(":reactive:core").name = "datasourcex-reactive-core"

include("reactive:api")

project(":reactive:api").name = "datasourcex-reactive-api"

include("reactive:java-flow")

project(":reactive:java-flow").name = "datasourcex-java-flow"

include("reactive:kotlin")

project(":reactive:kotlin").name = "datasourcex-kotlin"

include("reactive:reactivestreams")

project(":reactive:reactivestreams").name = "datasourcex-reactivestreams"

include("spring")

project(":spring").name = "spring-boot-starter-datasource"

include("version-catalog")

project(":version-catalog").name = "datasourcex-version-catalog"

include("api-docs")

include("examples:spring-java")

include("examples:spring-kotlin")

include("examples:spring-kotlin-chat")
