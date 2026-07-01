plugins { id("com.vanniktech.maven.publish") }

group = "com.caplin.integration.datasourcex"

val refName = System.getenv("GITHUB_REF_NAME") ?: "dev"

// Pre-release tags (X.Y.Z-rcN) publish as an overwriting SNAPSHOT to stay under Maven Central's
// monthly release/file limits; a clean X.Y.Z tag publishes a release. See docs/adr/0002.
version = if (refName.contains('-')) "${refName.substringBefore('-')}-SNAPSHOT" else refName

mavenPublishing {
  publishToMavenCentral()

  signAllPublications()

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
    val githubActor = System.getenv("GITHUB_ACTOR")
    val githubToken = System.getenv("GITHUB_TOKEN")
    if (githubActor != null && githubToken != null) {
      maven {
        name = "GitHubPackages"
        url =
            uri(
                "https://maven.pkg.github.com/caplin/DataSource-Extensions",
            )
        credentials {
          username = githubActor
          password = githubToken
        }
      }
    }
  }
}
