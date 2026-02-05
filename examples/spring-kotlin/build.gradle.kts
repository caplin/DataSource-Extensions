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
