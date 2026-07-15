plugins {
  kotlin("jvm")
  id("org.jetbrains.kotlin.plugin.spring")
  id("com.diffplug.spotless")
  alias(libs.plugins.spring.boot)
  application
}

spotless {
  kotlin { ktfmt(libs.versions.ktfmt.get()) }
  kotlinGradle { ktfmt(libs.versions.ktfmt.get()) }
}

application { mainClass.set("example.SpringKotlinApplicationKt") }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
  implementation(project(":spring-boot-starter-datasource"))
  implementation("org.springframework.boot:spring-boot-starter")
}

// Docker-backed end-to-end test: starts a Liberator Testcontainer, runs this adapter against it and
// subscribes as a StreamLink client. Not wired into `check`; run on demand with
// `./gradlew :examples:spring-kotlin:integrationTest`.
testing {
  suites {
    register<JvmTestSuite>("integrationTest") {
      dependencies {
        implementation(project())
        implementation(project(":datasourcex-kotest"))
        implementation(libs.kotest.runner)
        implementation(libs.kotest.assertions)
        implementation(libs.turbine)
      }
      targets.configureEach { testTask.configure { useJUnitPlatform() } }
    }
  }
}

configurations {
  named("integrationTestImplementation") { extendsFrom(configurations.implementation.get()) }
  named("integrationTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }
}
