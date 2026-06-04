# Module datasourcex-util

Utility classes and functions commonly used in DataSource integrations, such as efficiently observable Maps, and stream
transformations.

## Key components

### Observable maps and shared flows

| Component | Description |
| --- | --- |
| `FlowMap` / `MutableFlowMap` | A `Map` that is also observable via `asFlow`, `asFlowWithState`, and per-key `valueFlow`. Built with `mutableFlowMapOf` / `toMutableFlowMap`, or collected from an event stream with `flowMapIn`. |
| `CompletingSharedFlow` / `MutableCompletingSharedFlow` | A `SharedFlow` variant that also propagates completion and error events to subscribers. Built with `shareInCompleting`. |
| `SharedFlowCache` | Keyed cache of `SharedFlow`s, sharing one upstream collection per key and evicting a key once its upstream ends so the cache can't grow unbounded. Built with `sharedFlowCache(...)`, or `completingSharedFlowCache(...)` for a `CompletingSharedFlowCache` that propagates completion/errors. |
| `CompletingSharedFlowCache` / `LoadingCompletingSharedFlowCache` | Keyed cache of `CompletingSharedFlow`s, sharing one underlying collection per key. |

### Stores

| Component | Description |
| --- | --- |
| `FlowStore` / `MutableFlowStore` | A store-backed map exposing a delta-only stream plus a read-through, Caffeine-bounded cache. Built with `flowStore` / `flowStoreIn` / `mutableFlowStore`. |
| `AsyncFlowStore` / `AsyncMutableFlowStore` | Suspending views of the stores whose reads/writes dispatch the store I/O themselves. |
| `Store` / `StoreReader` / `StoreWriter` | The SPI you implement to back a `MutableFlowStore`; mutations enlist on the caller's transaction and publish on commit. |
| `TxContext` / `Versioned` | The transaction handle a write enlists on, and a value paired with the store-assigned version. |

### Flow operators

| Operator | Description |
| --- | --- |
| `bufferingDebounce` | Buffers elements until a quiet period elapses, then emits them as a `List`. |
| `throttleLatest` | Emits at most once per interval, keeping the latest value and dropping older ones. |
| `flatMapFirst` | Applies a function to the first element together with the entire upstream flow. |
| `demultiplexBy` | Groups elements by a key selector and processes each group's sub-flow. |
| `retryWithExponentialBackoff` | Retries the upstream on error with an exponential delay between attempts. |
| `cast` | Casts a `Flow<*>` to a `Flow<T>`. |
| `timeoutFirst` / `timeoutFirstOrNull` / `timeoutFirstOrDefault` | Errors / emits null / emits a default if no first element arrives within a timeout. |
| `materialize` / `dematerialize` (and `*Unboxed`) | Convert between flow values and `ValueOrCompletion` events. |

### Event models and folds

| Component | Description |
| --- | --- |
| `MapEvent` / `SimpleMapEvent` / `SetEvent` / `VersionedMapEvent` | Sealed event types describing map and set mutations (with or without old values / versions). |
| `FlowMapStreamEvent` / `ValueOrCompletion` | Materialised stream events: initial-state-plus-deltas, and value-or-terminal-signal. |
| `runningFoldToMap*` / `runningFoldToSet` | Fold a delta stream into a live `Flow` of map / set snapshots. |
| `conflateKeys` / `toEvents` | Collapse per-key updates, and expand a collection stream into entry events. |

### Serialization

| Component | Description |
| --- | --- |
| `registerDataSourceSerializers` / `registerPersistentCollectionSerializers` | Register Fory serializers for the event types and persistent collections. |
| `DataSourceModule`, `registerDataSourceModule` / `addDataSourceModule` | Jackson 2 / Jackson 3 modules that serialize the event types without annotations. |
| `Jackson2JsonHandler` / `Jackson3JsonHandler` | `JsonHandler` implementations backed by a Jackson `ObjectMapper`. |

### DataSource and general utilities

| Component | Description |
| --- | --- |
| `AntPatternNamespace` | A `Namespace` matching subjects by Ant-style patterns, with path-variable extraction. |
| `SimpleDataSourceFactory` / `SimpleDataSourceConfig` | Build a `DataSource` from a simplified config for tests and examples. |
| `KLogger` | An slf4j `Logger` wrapper with lazily-evaluated message lambdas. |
| `ReadWriteLock` | A non-reentrant suspending read/write lock. |
| `withTimeout` | A coroutine timeout that throws `TimeoutException` rather than a `CancellationException`. |

@see com.caplin.integration.datasourcex.util.flow.FlowMap  
@see com.caplin.integration.datasourcex.util.store.MutableFlowStore  

Participating in an application-owned jOOQ transaction:

@sample samples.StoreSamples.jooqSample
