# Product Guidelines

## Development Philosophy

### Readability & Maintainability
The primary focus for all development within this project is readability and maintainability. We favour clear, idiomatic Kotlin and Java that is easy for the maintenance team to understand and evolve. This means prioritizing expressive code over extreme performance micro-optimizations, unless a significant performance bottleneck is identified.

### Error Handling & Robustness
We follow a **Fail-Fast & Explicit** philosophy. Errors should be surfaced clearly to the user rather than being silently handled or ignored. While we use standard exceptions for error reporting, we strive to make error states predictable and minimize their occurrence through careful API design.

## Documentation & Knowledge Sharing

### Example-Driven Documentation
Our documentation philosophy is **Example-Driven**. We prioritize extensive, runnable code snippets and hands-on tutorials (like the existing `GUIDE.md`) to help developers integrate the library into their Spring Boot applications as quickly as possible. Every major feature should be accompanied by at least one clear example.

## Quality Assurance

### Comprehensive Integration Testing
Given the complex interactions between the reactive modules, the Spring Boot starter, and the Caplin DataSource platform, we prioritize **Comprehensive Integration Testing**. We focus on testing these components in realistic, end-to-end scenarios to ensure that the entire stack functions correctly under real-world conditions.

## Versioning & Compatibility

### Strict Semantic Versioning
We strictly adhere to **Semantic Versioning (SemVer)**. We guarantee that no breaking changes to the public API will be introduced without a major version increment. This ensures stability and trust for the financial application developers who depend on these extensions.
