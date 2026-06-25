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
  // Jackson 3 is the default JSON binding on this (Spring Boot 4) line; versions come from the
  // Spring Boot BOM.
  api("tools.jackson.core:jackson-databind")
  api("com.github.ben-manes.caffeine:caffeine")
  implementation("tools.jackson.module:jackson-module-kotlin")
  implementation(libs.zjsonpatch)

  // Jackson 2 support is opt-in: consumers that want the Jackson 2 serializers / JsonHandler must
  // bring Jackson 2 onto their own classpath (e.g. via spring-boot-jackson2). Kept compileOnly so
  // it
  // stays off the default (Jackson 3) runtime path.
  compileOnly("com.fasterxml.jackson.core:jackson-databind")
  compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
  compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

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
  // Jackson 2 is compileOnly in main; the Jackson 2 serialization/handler tests need it at runtime.
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

  jmh(libs.jmh.core)
  jmh(libs.jmh.generator)
  // JacksonSerializationBenchmark exercises both Jackson lines; Jackson 2 (compileOnly in main)
  // must
  // be added explicitly for the jmh runtime.
  jmh("com.fasterxml.jackson.module:jackson-module-kotlin")
  jmh("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

jmh { duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE) }

dokka {
  dokkaSourceSets.configureEach {
    includes.from("README.md")
    samples.from(layout.projectDirectory.dir("src/samples/kotlin"))
  }
}
