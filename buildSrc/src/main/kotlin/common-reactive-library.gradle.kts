plugins { id("common-library") }

val generateApi =
    tasks.register<GenerateApi>("generateApi") {
      outputDirectory = layout.buildDirectory.dir("generated/sources/$name/main/kotlin")
    }

sourceSets.main { kotlin.srcDir(generateApi) }

idea {
  module {
    generatedSourceDirs.add(
        layout.buildDirectory.dir("generated/sources/$name/main/kotlin").get().asFile,
    )
  }
}
