plugins {
  `common-library`
}

dependencies {
  api(project(":datasourcex-util"))
  api(project(":reactive:datasourcex-reactive-api"))
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation(libs.kotlin.collections.immutable)
  implementation("org.slf4j:slf4j-api")

  testRuntimeOnly("org.slf4j:slf4j-simple")
  testImplementation(libs.mockk)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.runner)
  testImplementation(libs.turbine)
}

apiValidation {
  ignoredPackages.add("com.caplin.integration.datasourcex.reactive.core")
}
