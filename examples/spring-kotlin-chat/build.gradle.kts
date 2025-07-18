plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.jetbrains.dokka")
  id("com.ncorti.ktfmt.gradle")
  alias(libs.plugins.spring.boot)
  application
}

dependencies {
  implementation(project(":spring-boot-starter-datasource"))
  implementation("org.springframework.boot:spring-boot-starter")
}

dokka { dokkaSourceSets.configureEach { includes.from("README.md") } }
