plugins {
  `kotlin-dsl`
  id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmt.plugin
}

dependencies {
  implementation(libs.kotlinpoet)

  implementation(libs.ktfmt.plugin)
  implementation(libs.dokka.plugin)
  implementation(libs.detekt.plugin)
  implementation(libs.binary.compatibility.validator.plugin)
  implementation(libs.kover.plugin)
  implementation(libs.kotlin.plugin)
  implementation(libs.kotlin.allopen.plugin)
  implementation(libs.vanniktech.maven.publish.plugin)
}
