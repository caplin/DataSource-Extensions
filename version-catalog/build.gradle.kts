import com.vanniktech.maven.publish.VersionCatalog

plugins {
  `common-maven`
  `version-catalog`
}

catalog {
  val springBootStarterDataSource = project(":spring-boot-starter-datasource")
  val util = project(":datasourcex-util")
  val reactiveApi = project(":reactive:datasourcex-reactive-api")
  val reactiveCore = project(":reactive:datasourcex-reactive-core")
  val javaFlow = project(":reactive:datasourcex-java-flow")
  val kotlin = project(":reactive:datasourcex-kotlin")
  val reactiveStreams = project(":reactive:datasourcex-reactivestreams")
  versionCatalog {
    version("spring-boot", libs.versions.springBoot.get())
    version(rootProject.name, util.version.toString())
    library(
        util.name,
        project.group.toString(),
        util.name,
    ).versionRef(rootProject.name)
    library(
        reactiveApi.name,
        project.group.toString(),
        reactiveApi.name,
    ).versionRef(rootProject.name)
    library(
        reactiveCore.name,
        project.group.toString(),
        reactiveCore.name,
    ).versionRef(rootProject.name)
    library(javaFlow.name, project.group.toString(), javaFlow.name).versionRef(
        rootProject.name,
    )
    library(kotlin.name, project.group.toString(), kotlin.name).versionRef(
        rootProject.name,
    )
    library(
        reactiveStreams.name,
        project.group.toString(),
        reactiveStreams.name,
    ).versionRef(rootProject.name)
    library(
        springBootStarterDataSource.name,
        project.group.toString(),
        springBootStarterDataSource.name,
    ).versionRef(rootProject.name)
  }
}

mavenPublishing {
  configure(VersionCatalog())
}
