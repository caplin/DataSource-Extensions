# Extensions for DataSource

Requires Kotlin 2.2 or later and JDK 17 or later.

## Compatibility

This library is maintained in two parallel lines — choose the version that matches your Spring Boot
major version:

| Library version | Branch              | Spring Boot | Default JSON binding                                                                                          | Kotlin  |
|------------------|---------------------|-------------|---------------------------------------------------------------------------------------------------------------|---------|
| `3.x`            | `main`              | 4.0.x       | Jackson 3 (Jackson 2 available via [`spring-boot-jackson2`](https://docs.spring.io/spring-boot/reference/features/json.html)) | 2.2.21+ |
| `2.x`            | `springboot-3.5.x`  | 3.5.x       | Jackson 2 (Jackson 3 available by adding the `tools.jackson` dependencies)                                     | 2.2+    |

Upgrading from `2.x` (Spring Boot 3.5) to `3.x` (Spring Boot 4.0)? See the
[migration guide](./MIGRATION.md).

## Reactive

These modules provide APIs for consuming and providing data as streams implementations provided by popular libraries to
DataSource subjects and channels.

| Module                      | Supports                                                                                                                                                                                                           |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| datasourcex-kotlin          | [Kotlin Flow](https://kotlinlang.org/docs/flow.html) and suspending functions                                                                                                                                      |
| datasourcex-java-flow       | [Java Flow](https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html)                                                                                                                              |
| datasourcex-reactivestreams | [Reactive Streams](https://www.reactive-streams.org) such as:<br/>[Reactor](https://projectreactor.io/) - Flux and Mono<br/>[RxJava](https://github.com/ReactiveX/RxJava) - Observable, Flowable, Single and Maybe |

### Usage

Add the required dependencies to your project:

```kotlin
dependencies {
    implementation("com.caplin.integration.datasourcex:datasourcex-<java-flow|kotlin|reactivestreams>:<version>")
}
```

Then refer to the documentation:

* [datasourcex-reactivestreams](https://caplin.github.io/DataSource-Extensions/reactive/datasourcex-reactivestreams)
* [datasourcex-kotlin](https://caplin.github.io/DataSource-Extensions/reactive/datasourcex-kotlin)
* [datasourcex-java-flow](https://caplin.github.io/DataSource-Extensions/reactive/datasourcex-java-flow)

## Spring

This module provides a starter for integrating Caplin DataSource with your
[Spring Boot 4.0](https://spring.io/projects/spring-boot) application, and integration with
[Spring Messaging](https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html)
for publishing data from annotated functions.

### Usage

Add the following dependency to your project:

```kotlin
dependencies {
    implementation("com.caplin.integration.datasourcex:spring-boot-starter-datasource:<version>")
}
```

Then refer to
the [documentation](https://caplin.github.io/DataSource-Extensions/spring-boot-starter-datasource),
[hands-on tutorial](./spring/docs/GUIDE.md) and [examples](./examples).