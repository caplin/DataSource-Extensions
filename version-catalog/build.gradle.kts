plugins {
    `common-maven`
    `version-catalog`
}

catalog {
    versionCatalog {
        from(files("$rootDir/gradle/libs.versions.toml"))
        version(rootProject.name, rootProject.version.toString())
        library("datasourcex-util", project.group.toString(), "datasourcex-util").versionRef(rootProject.name)
        library("datasourcex-reactive-api", project.group.toString(), "datasourcex-reactive-api").versionRef(rootProject.name)
        library("datasourcex-reactive-core", project.group.toString(), "datasourcex-reactive-core").versionRef(rootProject.name)
        library("datasourcex-java-flow", project.group.toString(), "datasourcex-java-flow").versionRef(rootProject.name)
        library("datasourcex-kotlin", project.group.toString(), "datasourcex-kotlin").versionRef(rootProject.name)
        library("datasourcex-reactivestreams", project.group.toString(), "datasourcex-reactivestreams").versionRef(rootProject.name)
        library("datasourcex-spring", project.group.toString(), "datasourcex-spring").versionRef(rootProject.name)
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["versionCatalog"])
        }
    }
}
