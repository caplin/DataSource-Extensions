# Examples

This directory contains examples of using the libraries contained within this repository.

| Module             | Description                                                                                                  |
|--------------------|--------------------------------------------------------------------------------------------------------------|
| spring-java        | Usage of the `spring-boot-starter-datasource` module from within a Java Spring Boot project                  |
| spring-kotlin      | Usage of the `spring-boot-starter-datasource` module from within a Kotlin Spring Boot project                |
| spring-kotlin-chat | A more fully featured example of the `spring-boot-starter-datasource` module implementing a chat application |

# Requirements

All examples require a Caplin Liberator to connect to provide their data. An example of which can be started from the
provided [Docker Compose file](compose.yaml):

* Authenticate with the Caplin Docker Registry with `docker login docker-release.caplin.com`. For credentials please speak to your Caplin Account Manager.
* Edit [the environment file](.env) so that `LICENSES_DIR` points to a directory containing a valid Liberator 8 license.
* Run `docker compose up -d` from within this directory.

If successful, you will then be able to locally access the [Liberator UI](http://localhost:18080).
Of particular use is the [Liberator Explorer UI](http://localhost:18080/diagnostics/liberatorexplorer_react/index.html)
which you can use to request data from the example applications. Due to `OpenPermissioning` being enabled within the
provided [Liberator's Dockerfile](docker/liberator/Dockerfile), any user/password combination will work for
authorization.