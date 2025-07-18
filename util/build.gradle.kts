plugins { `common-library` }

description = "Utility classes for DataSource extensions"

dependencies {
  api(platform(libs.spring.boot.dependencies))
  api(libs.datasource)
  api("org.slf4j:slf4j-api")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation(libs.kotlin.collections.immutable)

  testRuntimeOnly("org.slf4j:slf4j-simple")

  testImplementation("org.springframework:spring-core") // For testing the RegexPathMatcher
  testImplementation(libs.turbine)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.runner)
}

dokka { dokkaSourceSets.configureEach { includes.from("README.md") } }
