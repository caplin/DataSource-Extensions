dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven {
      url = uri("https://repository.caplin.com/repository/caplin-release")
      credentials {
        val caplinUsername: String? by settings
        val caplinPassword: String? by settings
        username = caplinUsername ?: System.getenv("CAPLIN_USERNAME")
        password = caplinPassword ?: System.getenv("CAPLIN_PASSWORD")
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

include("docs")
include("examples:spring-java")
include("examples:spring-kotlin")
include("examples:spring-kotlin-chat")
