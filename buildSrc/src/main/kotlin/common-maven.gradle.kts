plugins { id("com.vanniktech.maven.publish") }

group = "com.caplin.integration.datasourcex"

val configuredVersion =
    System.getenv("CI_COMMIT_TAG") ?: System.getenv("CI_COMMIT_REF_SLUG") ?: "dev"

val releaseTag = "^[0-9]+\\.[0-9]+\\.[0-9]+$".toRegex()

val preReleaseTag = "^([0-9]+\\.[0-9]+\\.[0-9]+)-rc[0-9]+$".toRegex()

// A pre-release tag (X.Y.Z-rcN) publishes as an overwriting X.Y.Z-SNAPSHOT, to caplin-ci and to Maven
// Central's snapshot repository — neither counts against Central's monthly release quota. A clean
// X.Y.Z tag releases.
version =
    preReleaseTag.matchEntire(configuredVersion)?.let { "${it.groupValues[1]}-SNAPSHOT" }
        ?: configuredVersion

mavenPublishing {
  // Release deployments land staged in the Central Portal and need releasing there by hand;
  // snapshots upload straight to the snapshot repository. See the publish_maven_central job.
  publishToMavenCentral()

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
      // caplin-release is immutable, so it takes release tags only; caplin-ci handles snapshots and
      // so takes everything else — rc tags and branch builds alike.
      val repository = if (releaseTag.matches(configuredVersion)) "release" else "ci"
      url = uri("https://artifactory.caplin.com/artifactory/caplin-$repository")
      credentials {
        username = System.getenv("ARTIFACTORY_USERNAME")
        password = System.getenv("ARTIFACTORY_PASSWORD")
      }
    }
  }
}
