# Public value types are plain classes, not `data class`es

Public API value types across both product lines (`datasourcex`, `sl4jx`) are declared as ordinary
classes with hand-written `equals`/`hashCode`/`toString` where value semantics are wanted, never
`data class`. A `data class` bakes `copy(...)` and `componentN()` into the binary-compatible surface,
so adding or reordering a constructor property later is a breaking change — the `copy`/`componentN`
signatures shift, and `@JvmOverloads` does not extend to the generated `copy`. Hand-writing the value
methods keeps the constructor free to evolve within a minor release. See
[Public API challenges in Kotlin](https://jakewharton.com/public-api-challenges-in-kotlin/).

## Consequences

- New public value types follow this directly — e.g. the sl4jx `UpdateEvent`/`StatusEvent`/
  `ErrorEvent` and `ContainerChange.*`, matching the existing `reactive.api` events (`ContainerEvent`,
  `BroadcastEvent`), which expose only the `componentN` they choose to hand-write.
- Losing `data` does not mean losing `copy`: hand-write it when it is wanted. Because you own the
  signature, a later parameter is added as an overload rather than silently shifting the generated
  one. `AntPatternNamespace` is the in-repo example — a plain class with a hand-written `copy`.
- A type may support destructuring by hand-writing `componentN`; it need not expose `copy`.
- A type that holds non-value fields (streams, containers, connections) is a plain class with no
  `equals`/`hashCode` at all, rather than a `data class` whose generated equality would be meaningless.
- Removing a shipped `data class`'s generated `copy`/`componentN` is itself binary-breaking, so a
  released type is converted by hand-writing whichever of those members callers already use; only a
  genuinely unused member is dropped outright.
