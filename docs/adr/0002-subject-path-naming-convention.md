# A subject's string form is named `path`; a `Subject` object is named `subject`

Across both product lines (`datasourcex` and `sl4jx`), a variable, parameter, or property is named
by the type it holds: `path` when it is the `String` rendering of a subject, `subject` when it is a
`Subject`. The convention applies to public and internal code alike and to the `buildSrc` code
generation, which emits `path: String` DSL parameters (`pathParameter`). Method names describe an
operation rather than a variable — `getSubject(subject: Subject)`, `extractPathParameters(path: String)` —
and are governed by what they do, not by this rule.

## Consequences

New and renamed members follow the convention directly. A rename that would break binary
compatibility — a public `subject: String` property, whose getter would change from `getSubject()` to
`getPath()` — is introduced by **deprecate-and-add**: the `path` member is added and the old name is
kept as a `@Deprecated` alias delegating to it. This keeps the convention shippable within a minor
(3.x) release. The deprecated aliases are removed in a later major release.
