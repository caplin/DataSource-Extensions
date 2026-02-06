plugins {
  id("org.jetbrains.kotlinx.kover")
  id("com.diffplug.spotless")
  base
}

spotless {
  kotlinGradle {
    ktfmt(libs.versions.ktfmt.get())
    target("*.gradle.kts")
  }
}

dependencies {
  kover(project(":reactive:datasourcex-reactive-api"))
  kover(project(":reactive:datasourcex-reactive-core"))
  kover(project(":reactive:datasourcex-java-flow"))
  kover(project(":reactive:datasourcex-kotlin"))
  kover(project(":reactive:datasourcex-reactivestreams"))
  kover(project(":spring-boot-starter-datasource"))
  kover(project(":datasourcex-util"))
}

kover { reports { filters { excludes { packages("samples") } } } }
