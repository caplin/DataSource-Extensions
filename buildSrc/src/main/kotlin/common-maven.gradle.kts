plugins { `maven-publish` }

group = "com.caplin.integration.datasourcex"

val configuredVersion =
    System.getenv("GITHUB_REF_NAME") ?: "dev"

version = configuredVersion

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "caplin/DataSource-Extensions"}")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}
