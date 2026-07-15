plugins { `common-library` }

description = "Kotlin based Reactive StreamLink extensions"

dependencies {
  api(platform(libs.spring.boot.dependencies))
  // Unifies on the shared util.Subject; transitively brings the DataSource server SDK onto the
  // client classpath — an accepted trade-off (see spec #62).
  api(project(":datasourcex-util"))
  api(libs.streamlink) { exclude(group = "junit") }
  api(libs.keymaster)
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  // Jackson 3 versions come from the Spring Boot BOM.
  api("tools.jackson.core:jackson-databind")
  api(libs.kotlin.collections.immutable)
  implementation("tools.jackson.module:jackson-module-kotlin")
  implementation(libs.zjsonpatch)
  implementation(libs.kotlin.logging)

  testRuntimeOnly("org.slf4j:slf4j-simple")
  testImplementation(libs.kotest.runner)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
}

// TODO This can be removed once PSL-889 is resolved, and we take a new StreamLink.
publishing {
  publications {
    withType<MavenPublication>().configureEach {
      pom {
        withXml {
          val root = asNode()
          val dependenciesNode =
              root.children().find { it is groovy.util.Node && it.name() == "dependencies" }
                  as? groovy.util.Node ?: return@withXml

          dependenciesNode
              .children()
              .filterIsInstance<groovy.util.Node>()
              .filter { it.name() == "dependency" }
              .filter {
                val groupId = it.get("groupId")?.toString()
                val artifactId = it.get("artifactId")?.toString()
                groupId == "com.caplin.streamlink" && artifactId == "StreamLinkJava"
              }
              .forEach { depNode ->
                val exclusions = depNode.appendNode("exclusions")
                val exclusion = exclusions.appendNode("exclusion")
                exclusion.appendNode("groupId", "junit")
                exclusion.appendNode("artifactId", "junit")
              }
        }
      }
    }
  }
}
