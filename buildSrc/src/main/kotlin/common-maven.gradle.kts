plugins { `maven-publish` }

group = "com.caplin.integration.datasourcex"

val configuredVersion =
    System.getenv("CI_COMMIT_TAG") ?: System.getenv("CI_COMMIT_REF_SLUG") ?: "dev"

version = configuredVersion

publishing {
  repositories {
    maven {
      url = uri("https://gitlab.caplin.com/api/v4/projects/273/packages/maven")
      credentials(HttpHeaderCredentials::class.java) {
        name = "Job-Token"
        value = System.getenv("CI_JOB_TOKEN")
      }
      authentication { create("header", HttpHeaderAuthentication::class.java) }
    }
    maven {
      url =
          uri(
              "https://artifactory.caplin.com/artifactory/caplin-${
            when {
              "^[0-9]+\\.[0-9]+\\.[0-9]+\$".toRegex().matches(configuredVersion) -> "release"
              "^[0-9]+\\.[0-9]+\\.[0-9]+-rc[0-9]+\$".toRegex().matches(configuredVersion) -> "rc"
              else -> "ci"
            }
          }",
          )
      credentials {
        username = System.getenv("ARTIFACTORY_USERNAME")
        password = System.getenv("ARTIFACTORY_PASSWORD")
      }
    }
  }
}
