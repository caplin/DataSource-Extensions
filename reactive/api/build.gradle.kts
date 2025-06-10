plugins {
  `common-library`
}

dependencies {
  api(project(":datasourcex-util"))

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
  ignoredPackages.add("com.caplin.integration.datasourcex.reactive.internal")
}

dokka {
  dokkaSourceSets.configureEach {
    includes.from("README.md")
  }
}
