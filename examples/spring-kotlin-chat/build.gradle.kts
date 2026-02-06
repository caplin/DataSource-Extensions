plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.jetbrains.dokka")
  id("com.diffplug.spotless")
  alias(libs.plugins.spring.boot)
  application
}

spotless {
  kotlin { ktfmt(libs.versions.ktfmt.get()) }
  kotlinGradle { ktfmt(libs.versions.ktfmt.get()) }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
  implementation(project(":spring-boot-starter-datasource"))
  implementation("org.springframework.boot:spring-boot-starter")
}

dokka { dokkaSourceSets.configureEach { includes.from("README.md") } }
