
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.gradle.api.file.DuplicatesStrategy.WARN

plugins {
  id("common-maven")
  kotlin("jvm")
  `java-library`
  idea
  id("org.jetbrains.kotlinx.kover")
  id("org.jetbrains.kotlinx.binary-compatibility-validator")
  id("com.diffplug.spotless")
  id("org.jetbrains.dokka")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val ktfmtVersion = libs.findVersion("ktfmt").get().requiredVersion

spotless {
  kotlin {
    ktfmt(ktfmtVersion)
    target("src/**/*.kt")
  }
  kotlinGradle {
    ktfmt(ktfmtVersion)
    target("*.gradle.kts")
  }
}

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
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

publishing {
  publications {
    withType<MavenPublication> {
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
