plugins {
  kotlin("jvm")
  id("org.jetbrains.kotlin.plugin.spring")
  id("com.ncorti.ktfmt.gradle")
  alias(libs.plugins.spring.boot)
  application
}

dependencies {
  implementation(project(":spring-boot-starter-datasource"))
  implementation("org.springframework.boot:spring-boot-starter")
}
