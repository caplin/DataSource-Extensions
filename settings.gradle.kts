pluginManagement {
    repositories {
        maven { url = uri("https://artifactory.caplin.com/artifactory/gradle-plugins/") }
        maven { url = uri("https://artifactory.caplin.com/artifactory/repo1") } // Caplin Maven Central cache
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://artifactory.caplin.com/artifactory/repo1") } // Caplin Maven Central cache
        maven { url = uri("https://artifactory.caplin.com/artifactory/thirdparty-repo") }
        maven { url = uri("https://artifactory.caplin.com/artifactory/caplin-release") }
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
