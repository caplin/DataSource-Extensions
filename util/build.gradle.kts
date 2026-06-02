plugins {
  `common-library`
  alias(libs.plugins.jmh)
}

description = "Utility classes for DataSource extensions"

dependencies {
  api(platform(libs.spring.boot.dependencies))
  // We supply our own JsonHandler implementations (see serialization.jackson2/jackson3) instead of
  // the SDK's JacksonJsonHandler, so the Jackson-2-only json-patch library is no longer needed at
  // runtime. diff/patch is handled by zjsonpatch (which supports both Jackson 2 and 3).
  api(libs.datasource) { exclude(group = "com.github.java-json-tools", module = "json-patch") }
  api("org.slf4j:slf4j-api")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
  api("com.fasterxml.jackson.core:jackson-core")
  api("com.github.ben-manes.caffeine:caffeine")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation(libs.zjsonpatch)

  // Jackson 3 support is opt-in: consumers that want the Jackson 3 serializers / JsonHandler must
  // bring Jackson 3 onto their own classpath. Kept compileOnly here so it stays off the default
  // (Jackson 2) runtime path.
  compileOnly(libs.jackson3.databind)
  compileOnly(libs.jackson3.module.kotlin)

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(libs.kotlin.collections.immutable)

  compileOnly(libs.fory.core)
  compileOnly(libs.fory.kotlin)

  // Samples (compiled for Dokka only): show MutableFlowStore participating in a blocking jOOQ
  // transaction. Off the published runtime classpath; jooq is version-managed by the Spring Boot
  // BOM.
  samplesImplementation("org.jooq:jooq")

  testRuntimeOnly("org.slf4j:slf4j-simple")

  testImplementation("org.springframework:spring-core") // For testing the RegexPathMatcher
  testImplementation(libs.turbine)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.runner)
  testImplementation(libs.mockk)
  testImplementation(libs.fory.core)
  testImplementation(libs.fory.kotlin)
  testImplementation(libs.jackson3.databind)
  testImplementation(libs.jackson3.module.kotlin)

  jmh(libs.jmh.core)
  jmh(libs.jmh.generator)
  jmh(libs.jackson3.databind)
  jmh(libs.jackson3.module.kotlin)
}

jmh { duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE) }

dokka {
  dokkaSourceSets.configureEach {
    includes.from("README.md")
    samples.from(layout.projectDirectory.dir("src/samples/kotlin"))
  }
}
