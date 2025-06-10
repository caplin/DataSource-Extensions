plugins {
  `common-reactive-library`
}

dependencies {
  api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")

  api(project(":reactive:datasourcex-reactive-api"))

  implementation(project(":reactive:datasourcex-reactive-core"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testRuntimeOnly("org.slf4j:slf4j-simple")
  testImplementation(libs.mockk)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.runner)
  testImplementation(libs.turbine)

  samplesImplementation("io.projectreactor:reactor-core")
}

tasks.generateApi {
  publisherType = "reactivestreams"
}

dokka {
  dokkaSourceSets.configureEach {
    includes.from("README.md")
    samples.from(layout.projectDirectory.dir("src/samples/kotlin"))
  }
}
