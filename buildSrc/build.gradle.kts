plugins {
  `kotlin-dsl`
  id("com.diffplug.spotless") version libs.versions.spotless.plugin
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.kotlinpoet)

  implementation(libs.spotless.plugin)
  implementation(libs.dokka.plugin)
  implementation(libs.binary.compatibility.validator.plugin)
  implementation(libs.kover.plugin)
  implementation(libs.kotlin.plugin)
  implementation(libs.kotlin.allopen.plugin)
  implementation(libs.vanniktech.maven.publish.plugin)
}

spotless {
  kotlinGradle { ktfmt(libs.versions.ktfmt.get()) }
  kotlin { ktfmt(libs.versions.ktfmt.get()) }
}
