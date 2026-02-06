plugins {
  java
  id("org.jetbrains.kotlin.plugin.spring")
  id("com.diffplug.spotless")
  alias(libs.plugins.spring.boot)
  application
}

spotless {
  java {
    googleJavaFormat()
    target("src/**/*.java")
  }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
  implementation(project(":spring-boot-starter-datasource"))
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.jspecify:jspecify:1.0.0")
}
