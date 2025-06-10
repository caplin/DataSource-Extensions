plugins {
  `common-dokka`
}

dependencies {
  dokka(project(":reactive:datasourcex-reactive-api"))
  dokka(project(":reactive:datasourcex-java-flow"))
  dokka(project(":reactive:datasourcex-kotlin"))
  dokka(project(":reactive:datasourcex-reactivestreams"))
  dokka(project(":spring-boot-starter-datasource"))
  dokka(project(":datasourcex-util"))
}

dokka {
  moduleName.set("DataSource Extensions")
}
