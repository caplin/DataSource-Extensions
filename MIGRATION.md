# Migration guide

## 2.x (Spring Boot 3.5) → 3.x (Spring Boot 4.0)

Version `3.x` targets **Spring Boot 4.0** and makes **Jackson 3** (`tools.jackson.*`) the default
JSON binding, matching Spring Boot 4's own default. To stay on Spring Boot 3.5, remain on the
[`2.x` line](./README.md#compatibility) (branch `springboot-3.5.x`).

### Prerequisites

- Spring Boot **4.0.x**
- Kotlin **2.2.21+**
- JDK **17+** (unchanged)

### 1. Bump the dependency

```kotlin
dependencies {
    implementation("com.caplin.integration.datasourcex:spring-boot-starter-datasource:3.+")
    // or, for the reactive / util modules:
    implementation("com.caplin.integration.datasourcex:datasourcex-kotlin:3.+")
}
```

### 2. Jackson 3 is now the default

Spring Boot 4 auto-configures a Jackson 3 `tools.jackson.databind.ObjectMapper`, and the starter
wires a Jackson-3-backed `JsonHandler` onto the DataSource by default. For most applications no
change is required — plain POJOs serialize as before.

If you registered **custom Jackson 2 modules, serializers, or `ObjectMapper` customizers**, port them
to Jackson 3. See the
[Jackson 3 release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0).

### 3. Keeping Jackson 2 (optional)

To keep using Jackson 2 for DataSource JSON, add Spring Boot's (deprecated) Jackson 2 module and
define your own `JsonHandler` bean. The starter's handler is `@ConditionalOnMissingBean`, so yours
takes precedence:

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-jackson2")
```

```kotlin
import com.caplin.datasource.messaging.json.JsonHandler
import com.caplin.integration.datasourcex.util.SimpleDataSourceFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean

@Bean
fun dataSourceJsonHandler(objectMapper: ObjectMapper): JsonHandler<*> =
    SimpleDataSourceFactory.createJackson2JsonHandler(objectMapper)
```

Outside Spring, `SimpleDataSourceFactory.createDataSource(...)` now defaults to the Jackson 3
handler; pass `SimpleDataSourceFactory.defaultJackson2JsonHandler` (or your own) explicitly to keep
Jackson 2. On the `3.x` line the Jackson 2 artifacts are `compileOnly` in `datasourcex-util`, so add
them to your own classpath if you use the Jackson 2 helpers directly.
