plugins {
  java
  id("org.jetbrains.kotlin.plugin.spring")
  alias(libs.plugins.spring.boot)
  application
}

dependencies {
  implementation(project(":spring-boot-starter-datasource"))
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.jspecify:jspecify:1.0.0")
}
