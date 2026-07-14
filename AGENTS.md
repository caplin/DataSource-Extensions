# AGENTS.md

Guidance for AI coding assistants working in this repository.

## Project

`DataSource-Extensions` is a multi-module Gradle (Kotlin DSL) library that wraps Caplin's `com.caplin.platform.integration.java:datasource` SDK with modern reactive APIs and a Spring Boot starter. Kotlin Coroutines `Flow` is the canonical internal representation; Java `Flow.Publisher` and Reactive Streams `Publisher` variants are thin adapters over it.

JDK 17. Two parallel release lines (see the compatibility table in `README.md`): **`main` targets Spring Boot 4.0.x** (Jackson 3 is the default JSON binding; Jackson 2 is a `compileOnly` opt-in), and **`springboot-3.5.x` is the Spring Boot 3.5.x maintenance branch** (Jackson 2 default). Kotlin pinned to 2.2.21 (see `gradle/libs.versions.toml`). The `common-library` convention plugin applies `io.spring.dependency-management` and overrides Spring's BOM `kotlin.version` to our catalog value — without this, transitive Jackson updates raise `kotlin-stdlib` past what the compiler can read.

`datasourcex-util` ships both Jackson serialization layers under `serialization/{jackson2,jackson3}` (mirrored serializers + a `JsonHandler` each, sharing zjsonpatch for RFC 6902 diff/patch). On `main`, Jackson 3 (`tools.jackson.*`) is the runtime default and Jackson 2 (`com.fasterxml.jackson.*`) is `compileOnly`; on `springboot-3.5.x` it's the reverse. The Spring starter's `JsonHandler` bean auto-selects Jackson 3 when present and falls back to Jackson 2 (`DataSourceAutoConfiguration`).

## Common commands

The repo is typically checked out on Windows. PowerShell uses `.\gradlew.bat`; Bash uses `./gradlew`. Both work.

```
./gradlew classes                       # compile everything (what CI runs first)
./gradlew --continue check koverXmlReport  # full test + coverage (what CI runs)
./gradlew test                          # tests only
./gradlew :reactive:datasourcex-kotlin:test  # tests for one module
./gradlew :reactive:datasourcex-kotlin:test --tests "*.ClassName.methodName"  # single test
./gradlew spotlessApply                 # auto-format Kotlin + Gradle files (REQUIRED before commit)
./gradlew spotlessCheck                 # verify formatting
./gradlew apiCheck                      # binary-compatibility check (see "Public API" below)
./gradlew apiDump                       # regenerate .api snapshots
./gradlew dokkaGenerate                 # build docs site (output in api-docs/build/dokka/html)
./gradlew :examples:spring-kotlin:bootRun  # run an example app against a local Liberator
```

`CAPLIN_USERNAME` and `CAPLIN_PASSWORD` must be set in the environment (or as `caplinUsername`/`caplinPassword` Gradle properties) — without them, Gradle dependency resolution fails because the Caplin DataSource jar lives in a private Maven repo. See `settings.gradle.kts`.

## Architecture

### Module layout and dependency direction

```
reactive/datasource/api          ← config DSL types (ActiveConfig, ChannelConfig, ContainerEvent, …)
reactive/datasource/core         ← Binder + IFlowAdapter — does the real DataSource SDK plumbing
                                   Everything funnels through here as kotlinx.coroutines Flow
reactive/datasource/kotlin       ← Bind DSL for Flow                    } each contributes a Bind facade
reactive/datasource/java-flow    ← Bind DSL for java.util.concurrent.Flow.Publisher
reactive/datasource/reactivestreams ← Bind DSL for org.reactivestreams.Publisher

spring                ← spring-boot-starter-datasource (depends on reactive/datasource/kotlin)
util                  ← datasourcex-util — FlowMap, custom Flow operators, AntPatternNamespace,
                        Fory serialization helpers. No DataSource SDK dependency.
examples/             ← spring-java, spring-kotlin, spring-kotlin-chat — manual smoke tests
api-docs/             ← aggregated Dokka site (published to GitHub Pages from main)
```

The five reactive modules sit under `reactive/datasource/` on disk so the StreamLink SDK family can sit beside them under `reactive/` later. A `projectDir` override in `settings.gradle.kts` relocates only the directory; the `include("reactive:kotlin")` coordinate, the resulting project path (`:reactive:datasourcex-kotlin`, `:reactive:datasourcex-reactive-core`, etc. — set by the `.name` overrides), and the published Maven coordinates are all unchanged. Published coordinates use the renamed name (`datasourcex-kotlin`, `spring-boot-starter-datasource`, etc.), not the include path.

### Code generation (read this before editing reactive modules)

The three `reactive/datasource/{kotlin,java-flow,reactivestreams}` modules are NOT three parallel hand-written implementations. Each runs a `generateApi` Gradle task (defined in `buildSrc/src/main/kotlin/GenerateApi.kt`, applied via `common-reactive-library`) that emits Kotlin source for the `Bind`, `BindActive`, `BindActiveContainer`, `BindChannel`, `BindBroadcast` DSL classes plus their `Mapping`/`Json`/`Record` flavours.

Each module's `build.gradle.kts` sets the variant: `tasks.generateApi { publisherType = "kotlin" | "java" | "reactivestreams" }`.

Implications:
- If you grep for `BindActiveJson` and find nothing, that's because it's generated. Run `./gradlew :reactive:datasourcex-kotlin:generateApi` and look in `reactive/datasource/kotlin/build/generated/sources/generateApi/main/kotlin/`.
- The shape of the generated DSL lives in `buildSrc/src/main/kotlin/{Active,ActiveContainer,Channel,Broadcast,Functions}.kt`. Edit those to change the generated API, not the build output.
- All three published variants share the runtime path through `reactive/datasource/core/Binder`. `IFlowAdapter` is the small bridge that converts each library's publisher type to/from `Flow<Any>`.

### Spring starter

`spring` depends on `reactive/datasource/kotlin` and exposes endpoints via custom annotations on top of Spring Messaging:
- `@DataService` marks a controller. `@DataMessageMapping("/subject/{var}")` maps a subject pattern to a method (analogous to `@MessageMapping`).
- `@IngressDestinationVariable` extracts path variables.
- Methods return `Flow<T>` / `Flux<T>` / `Publisher<T>` for active subjects, or accept/return `Flow` pairs for channels.
- Wiring lives in `spring/src/main/kotlin/.../internal/` — `DataSourceAutoConfiguration`, `DataSourceMessageHandler`, `DataSourcePayloadMethodArgumentResolver`, `DataSourcePayloadReturnValueHandler`.
- The internal package is excluded from binary-compatibility validation (see `apiValidation` block in `spring/build.gradle.kts`).

The hands-on guide is `spring/docs/GUIDE.md`.

## Public API discipline

These libraries follow [Semantic Versioning](https://semver.org/). The `.api` snapshots are the source of truth for what counts as the public surface, so any diff to an `api/<module-name>.api` file is a deliberate signal about the next release:

- **Additions only** (new classes, new members) → minor bump.
- **Removals, renames, signature changes, or visibility reductions** → major bump. Flag these in the PR description; don't slip them through.
- **No `.api` diff** → patch bump is fine.

`org.jetbrains.kotlinx.binary-compatibility-validator` is applied to every published library and enforces this: `./gradlew check` runs `apiCheck` and fails if the public ABI drifts from the committed snapshot.

When you intentionally change public API: run `./gradlew apiDump`, review the diff carefully (it tells you what version bump is required), and commit the updated `.api` files alongside the source change. Don't hand-edit them.

## Conventions

- Formatting: `ktfmt` via Spotless. Always run `./gradlew spotlessApply` before committing — CI doesn't auto-format.
- Tests: JUnit 5 platform, Kotest assertions (`shouldBe`), MockK for mocks, Turbine for `Flow` assertions.
- `samples` source set: each library module has a `src/samples/kotlin/` directory whose contents are pulled into Dokka output. The `samples` package is excluded from coverage (see root `build.gradle.kts`).
- Kover aggregates coverage across all published modules at the root project; per-module reports also exist.

## Agent skills

### Issue tracker

Issues are tracked as GitHub issues in `caplin/DataSource-Extensions` (via the `gh` CLI); external pull requests are also pulled into the triage queue. See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary — `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.