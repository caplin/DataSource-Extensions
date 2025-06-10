dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("https://artifactory.caplin.com/artifactory/repo1")
    } // Caplin Maven Central cache
    maven { url = uri("https://artifactory.caplin.com/artifactory/thirdparty-repo") }
    maven { url = uri("https://artifactory.caplin.com/artifactory/caplin-release") }
  }
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
