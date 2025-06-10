import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.file.DuplicatesStrategy.WARN

plugins {
  id("common-maven")
  id("common-dokka")
  kotlin("jvm")
  `java-library`
  idea
  id("org.jetbrains.kotlinx.kover")
  id("org.jetbrains.kotlinx.binary-compatibility-validator")
  id("com.ncorti.ktfmt.gradle")
  id("io.gitlab.arturbosch.detekt")
}

java {
  sourceCompatibility = VERSION_17
  targetCompatibility = VERSION_17
  withSourcesJar()
}

kotlin { compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn") } }

tasks.jar { duplicatesStrategy = WARN }

tasks.test {
  useJUnitPlatform()
  reports {
    junitXml.required.set(true)
  }
}

dokka {
  dokkaSourceSets {
    configureEach {
      jdkVersion.set(17)
      externalDocumentationLinks.register("coroutines") {
        url = uri("https://kotlinlang.org/api/kotlinx.coroutines/")
      }
    }
  }
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      from(components["java"])
      versionMapping { allVariants { fromResolutionResult() } }
    }
  }
}

sourceSets {
  create("samples") {
    compileClasspath += main.get().output
    runtimeClasspath += main.get().output
  }
}

val samplesImplementation: Configuration by
    configurations.getting { extendsFrom(configurations.implementation.get()) }
