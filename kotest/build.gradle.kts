plugins { `common-library` }

description = "Kotest test-support: a Liberator-in-a-container harness for StreamLink client tests"

dependencies {
  api(platform(libs.spring.boot.dependencies))
  api(project(":reactive:sl4jx-kotlin"))
  api(libs.testcontainers)
  api(libs.kotest.framework.engine)
  api(libs.kotest.extensions.testcontainers)
  // jacksonObjectMapper() default; sl4jx-kotlin scopes jackson-module-kotlin as implementation, so
  // it is not transitive here (jackson-databind is, via sl4jx-kotlin's api).
  implementation("tools.jackson.module:jackson-module-kotlin")
}

// Docker-backed integration test against a live Liberator; intentionally not wired into `check`.
testing {
  suites {
    register<JvmTestSuite>("integrationTest") {
      useJUnitJupiter()
      dependencies {
        implementation(project())
        implementation(libs.kotest.runner)
        implementation(libs.kotest.assertions)
        implementation(libs.turbine)
        runtimeOnly("org.slf4j:slf4j-simple")
      }
    }
  }
}

// Keep the docker-backed integration test off `check`: Kover otherwise triggers every Test task
// when generating coverage, which `check` depends on.
kover { currentProject { instrumentation { disabledForTestTasks.add("integrationTest") } } }

dokka {
  dokkaSourceSets.configureEach {
    includes.from("README.md")
    samples.from(layout.projectDirectory.dir("src/samples/kotlin"))
  }
}
