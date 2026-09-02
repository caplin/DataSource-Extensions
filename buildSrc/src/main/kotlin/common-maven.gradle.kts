import com.vanniktech.maven.publish.SonatypeHost.Companion.CENTRAL_PORTAL

plugins { id("com.vanniktech.maven.publish") }

group = "com.caplin.integration.datasourcex"

val configuredVersion =
    System.getenv("CI_COMMIT_TAG") ?: System.getenv("CI_COMMIT_REF_SLUG") ?: "dev"

version = configuredVersion

mavenPublishing {
  // Kept registered but not run by default CI — Artifactory is the active publish target.
  // See the manual, tag-gated publish_maven_central job in .gitlab-ci.yml.
  publishToMavenCentral(CENTRAL_PORTAL)

  // Only sign when a signing key is configured (as CI does via ORG_GRADLE_PROJECT_signingInMemoryKey).
  // Local builds and publishToMavenLocal have no key and must not require one.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) {
    signAllPublications()
  }

  pom {
    name = project.name
    description = project.description ?: "Extension Library for Caplin DataSource"
    url = "https://github.com/caplin/DataSource-Extensions"
    inceptionYear = "2025"

    developers {
      developer {
        name = "Ross Anderson"
        organization = "Caplin Systems Ltd."
        url.set("https://github.com/rossdanderson")
      }
    }

    issueManagement {
      url = "https://github.com/caplin/DataSource-Extensions/issues"
      system = "GitHub"
    }

    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    scm {
      url.set("https://github.com/caplin/DataSource-Extensions")
      connection.set("scm:git:git://github.com/caplin/DataSource-Extensions.git")
      developerConnection.set("scm:git:ssh://git@github.com/caplin/DataSource-Extensions.git")
    }
  }
}

publishing {
  repositories {
    maven {
      name = "Artifactory"
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
