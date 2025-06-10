plugins {
  `kotlin-dsl`
  id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmtPlugin
}

repositories {
  maven {
    url = uri("https://artifactory.caplin.com/artifactory/repo1")
  } // Caplin Maven Central cache
  maven { url = uri("https://artifactory.caplin.com/artifactory/thirdparty-repo") }
  maven { url = uri("https://artifactory.caplin.com/artifactory/caplin-release") }
  maven { url = uri("https://plugins.gradle.org/m2/") }
  mavenCentral()
}

dependencies {
  implementation(libs.ktfmt.plugin)
  implementation(libs.dokka.plugin)
  implementation(libs.detekt.plugin)
  implementation(libs.binary.compatibility.validator.plugin)
  implementation(libs.kover.plugin)
  implementation(libs.kotlin.plugin)
  implementation(libs.kotlin.allopen.plugin)
  implementation(libs.kotlinpoet.plugin)
}
