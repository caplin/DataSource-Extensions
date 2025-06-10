# Extensions for DataSource

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
    implementation("com.caplin.integration:datasourcex-<java-flow|kotlin|reactivestreams>:<version>")
}
```

Then refer to the documentation:

* [datasourcex-reactivestreams](http://integration.pages.caplin.com/datasource-extensions/reactive/datasourcex-reactivestreams)
* [datasourcex-kotlin](http://integration.pages.caplin.com/datasource-extensions/reactive/datasourcex-kotlin)
* [datasourcex-java-flow](http://integration.pages.caplin.com/datasource-extensions/reactive/datasourcex-java-flow)

## Spring

This module provides a starter for integrating Caplin DataSource with your
[Spring Boot](https://spring.io/projects/spring-boot) application, and integration with
[Spring Messaging](https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html)
for publishing data from annotated functions.

### Usage

Add the following dependency to your project:

```kotlin
dependencies {
    implementation("com.caplin.integration:spring-boot-starter-datasource:<version>")
}
```

Then refer to
the [documentation](http://integration.pages.caplin.com/datasource-extensions/spring-boot-starter-datasource) and
the [examples](./examples).