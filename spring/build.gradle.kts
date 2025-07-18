plugins {
  `common-library`
  id("org.jetbrains.kotlin.plugin.spring")
  id("org.jetbrains.kotlin.kapt")
}

description = "Spring Boot integration for DataSource"

dependencies {
  api(project(":reactive:datasourcex-kotlin"))
  api("org.springframework:spring-messaging")
  api("io.projectreactor:reactor-core")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-starter-json")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation(libs.json.patch)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.slf4j:slf4j-api")

  kapt(libs.spring.boot.configuration.processor)

  testRuntimeOnly("org.slf4j:slf4j-simple")
  testImplementation(libs.mockk)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.runner)
  testImplementation(libs.turbine)
}

val prepareReadme =
    tasks.register<Copy>("prepareReadme") {
      from(layout.projectDirectory.file("README.md"))
      expand("springBootVersion" to libs.versions.springBoot.get())
      into(layout.buildDirectory.dir("readme"))
    }

dokka {
  dokkaSourceSets.configureEach {
    includes.from(prepareReadme.map { it.destinationDir.resolve("README.md") })
  }
}

apiValidation { ignoredPackages.add("com.caplin.reactive.datasource.spring.internal") }
