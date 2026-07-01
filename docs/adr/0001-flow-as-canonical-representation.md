# Coroutines `Flow` is the canonical internal representation

Kotlin Coroutines `Flow` is the single internal representation for all data moving through the library; the
`reactive/core` `Binder` does its DataSource SDK plumbing purely in terms of `Flow`. The
`java.util.concurrent.Flow.Publisher` and Reactive Streams `Publisher` variants are thin adapters over it (
`IFlowAdapter`), not parallel implementations — so the SDK integration is written and tested once.

## Consequences

The three `reactive/{kotlin,java-flow,reactivestreams}` Bind DSLs are **code-generated**, not hand-written: a
`generateApi` Gradle task emits each variant from shared templates in `buildSrc`, differing only by publisher type.
Grepping for a generated class (e.g. `BindActiveJson`) finds nothing in the source tree — edit the templates in
`buildSrc/src/main/kotlin/`, not the generated output.
