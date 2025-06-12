import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import gradle.kotlin.dsl.accessors._94cffe4e74c4f6a3b1c88c3e0c336ef5.mavenPublishing
import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.file.DuplicatesStrategy.WARN

plugins {
  id("common-maven")
  kotlin("jvm")
  `java-library`
  idea
  id("org.jetbrains.kotlinx.kover")
  id("org.jetbrains.kotlinx.binary-compatibility-validator")
  id("com.ncorti.ktfmt.gradle")
  id("io.gitlab.arturbosch.detekt")
  id("org.jetbrains.dokka")
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
  reports { junitXml.required.set(true) }
}

dokka {
  dokkaSourceSets {
    configureEach {
      jdkVersion.set(17)
      externalDocumentationLinks.register("coroutines") {
        url = uri("https://kotlinlang.org/api/kotlinx.coroutines/")
      }
      sourceLink {
        localDirectory = rootDir
        remoteUrl.set(uri("https://github.com/caplin/DataSource-Extensions/tree/main"))
      }
    }
  }
}

mavenPublishing {
  configure(
      KotlinJvm(
          javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
          sourcesJar = true,
      )
  )
}

sourceSets {
  create("samples") {
    compileClasspath += main.get().output
    runtimeClasspath += main.get().output
  }
}

val samplesImplementation: Configuration by
    configurations.getting { extendsFrom(configurations.implementation.get()) }
