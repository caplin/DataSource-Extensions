plugins {
  `common-reactive-library`
}

dependencies {
  api(project(":reactive:datasourcex-reactive-api"))
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  implementation(project(":reactive:datasourcex-reactive-core"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testRuntimeOnly("org.slf4j:slf4j-simple")
  testImplementation(libs.mockk)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.runner)
  testImplementation(libs.turbine)
}

tasks.generateApi {
  publisherType = "kotlin"
}

dokka {
  dokkaSourceSets.configureEach {
    includes.from("README.md")
    samples.from(layout.projectDirectory.dir("src/samples/kotlin"))
  }
}
