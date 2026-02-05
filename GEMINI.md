# Gemini Context: DataSource-Extensions

This document provides context and instructions for working with the `DataSource-Extensions` project.

## Project Overview

`DataSource-Extensions` is a collection of libraries and extensions for the Caplin DataSource platform. It focuses on modernizing integrations using Reactive programming models and Spring Boot.

**Key Technologies:**
*   **Languages:** Kotlin (primary), Java
*   **Build System:** Gradle (Kotlin DSL)
*   **Frameworks:** Spring Boot 3.x, Caplin DataSource, Project Reactor, Kotlin Coroutines (Flow)
*   **Testing:** Kotest, Turbine, MockK, JUnit 5

## Architecture & Modules

The project is a multi-module Gradle project:

*   **`datasourcex-reactive-core` & `api`**: Core abstractions for reactive data sources.
*   **`datasourcex-kotlin`**: Support for Kotlin Flow and Coroutines.
*   **`datasourcex-java-flow`**: Support for Java Flow (Flow API).
*   **`datasourcex-reactivestreams`**: Support for Reactive Streams (Reactor Flux/Mono, RxJava).
*   **`spring-boot-starter-datasource`**: A Spring Boot starter for easy integration, utilizing `@MessageMapping` for real-time endpoints.
*   **`datasourcex-util`**: Utility classes.
*   **`examples`**: Example applications (Spring Java/Kotlin, Chat app).

## Building and Running

### Prerequisites
*   JDK 17+
*   `CAPLIN_USERNAME` and `CAPLIN_PASSWORD` environment variables are required to resolve dependencies from the Caplin repository.

### Gradle Commands

*   **Build:**
    ```bash
    ./gradlew build
    ```
*   **Test:**
    ```bash
    ./gradlew test
    ```
*   **Format Code (Apply):**
    ```bash
    ./gradlew spotlessApply
    ```
*   **Check Code Formatting:**
    ```bash
    ./gradlew spotlessCheck
    ```
*   **Run Example (Spring Boot):**
    ```bash
    ./gradlew :examples:spring-kotlin:bootRun
    ```

## Development Conventions

*   **Code Style:** The project uses `spotless` with `ktfmt` for code formatting. Always run `./gradlew spotlessApply` before committing.
*   **Testing:**
    *   Use **Kotest** for assertions (`shouldBe`, etc.).
    *   Use **Turbine** for testing Kotlin Flows.
    *   Use **MockK** for mocking dependencies.
    *   Tests are typically JUnit 5 based.
*   **Documentation:** KDoc is used for API documentation. The `docs` module and `spring/docs/GUIDE.md` contain usage guides.

## Common Tasks

### Adding a new Reactive Endpoint (Spring)
1.  Create a `@Controller`.
2.  Annotate a function with `@MessageMapping("/subject")`.
3.  Return a `Flow<T>` (Kotlin) or `Flux<T>` (Java).

### Testing a Flow
Use `turbine` to verify the emission of items:
```kotlin
myFlow.test {
    awaitItem() shouldBe expectedItem
    awaitComplete()
}
```

## Key Files & Directories

*   `spring/docs/GUIDE.md`: Comprehensive guide on using the Spring Boot starter.
*   `buildSrc/src/main/kotlin/common-library.gradle.kts`: Shared build logic for libraries.
*   `gradle/libs.versions.toml`: Version catalog for dependency management.
