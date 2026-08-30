---
description: 'Guidelines for building Java base applications'
applyTo: '**/*.java'
---

# Java Development

## General Instructions

- Address code smells proactively during development rather than accumulating technical debt.
- Focus on readability, maintainability, and performance when refactoring identified issues.
- Use IDE / Code editor reported warnings and suggestions to catch common patterns early in development.

## Best practices

- **Records**: For classes primarily intended to store data (e.g., DTOs, immutable data structures), **Java Records should be used instead of traditional classes**.
- **Pattern Matching**: Utilize pattern matching for `instanceof` and `switch` expressions to simplify conditional logic and type casting.
- **Type Inference**: Use `var` for local variable declarations to improve readability, but only when the type is explicitly clear from the right-hand side of the expression.
- **Immutability**: Favor immutable objects. Make classes and fields `final` when possible. Do not mark local variables or method parameters as `final`; prefer ordinary declarations and only use `final` for fields or constants when it improves clarity. Use collections from `List.of()`/`Map.of()` for fixed data. Use `Stream.toList()` to create immutable lists.
- **Streams and Lambdas**: Use the Streams API and lambda expressions for collection processing. Employ method references (e.g., `stream.map(Foo::toBar)`).
- **Null Handling**: Avoid returning or accepting `null`. Use `Optional<T>` for possibly-absent values. Use JSpecify annotations to understand where null is allowed to skip unnecessary null checks.
- **JSpecify-first null guards**: In `@NullMarked` scope, do not use `Objects.requireNonNull(...)` for method parameters, constructor parameters, or record components that are non-null by default. Keep or add explicit checks only for truly nullable inputs (`@Nullable`, `@NullUnmarked`), external/deserialized data, framework entrypoints, or when a specific exception/message is part of the contract.
- **JavaDoc for Public Contracts**: Add short JavaDoc comments to public classes, interfaces, and methods when they define a contract. Describe the responsibility, intent, inputs, outputs, and guarantees of the API, not obvious implementation details. Do not add JavaDoc to methods overriding a base class or interface contract unless they need to document important implementation-specific behavior or constraints.
- **JSON Round-tripping for DTOs and contracts**: Any record, class, interface, or sealed hierarchy used as API input/output, queue payload, persistence model, or message contract must be serializable and deserializable with Jackson without losing subtype information. For polymorphic/sealed types, add `@JsonTypeInfo` and `@JsonSubTypes` with a discriminator property such as `"type"`. Validate by round-tripping a representative payload in a test before finalizing the change.
- **Formatting**: keep text lines not longer than 140 characters
- **Constructors**: should be without logic, replaced with @RequiredArgsConstructors so that required final fields are initialized

### Refactor Patterns (Mandatory for New Refactors)

- **No nullable lifecycle fields**: For fields that are initialized in one phase and consumed later (for example entity state/context), do not model lifecycle with `null`.
  Use explicit types to represent lifecycle states, for example a sealed interface with `Uninitialized` and `Initialized` variants.
- **Guard methods over implicit null assumptions**: Expose `require...()` methods that pattern-match on explicit variants and throw `IllegalStateException`
  with actionable messages when used out of lifecycle order.
- **Split oversized function classes**: For Azure Functions and similar adapters, keep one trigger entrypoint per file/class and delegate shared
  behavior to a dedicated support/service class.
- **Keep compatibility anchors stable**: Preserve shared constants (for example function names/entity names) in a stable class to minimize
  churn in tests and references during refactors.
- **Refactor tests with production split**: When splitting production entrypoints, split tests accordingly (contracts, triggers, orchestrator,
  activities, Spring wiring), and extract shared test helpers to reduce duplication.

### Naming Conventions

- Follow Google's Java style guide:
  - `UpperCamelCase` for class and interface names.
  - `lowerCamelCase` for method and variable names.
  - `UPPER_SNAKE_CASE` for constants.
  - `lowercase` for package names.
- Use nouns for classes (`UserService`) and verbs for methods (`getUserById`).
- Avoid abbreviations and Hungarian notation.
- Prefer concise JUnit test names that begin with `should`, for example `shouldMatchGeneratedFunctionMetadataWithAnnotationEntryPoints()`. Avoid long `given/when/then` style names unless a case truly requires them.

### Spring patterns
- Avoid generating constructor, use Lombok @RequiredArgsConstructor to inject dependencies
- if a class is a Spring bean (like @Component) and additional creation-related logic should be invoked, create method `init' marked with @PostConstruct
- do not check nullability of injected dependencies for Spring beans - it is guaranted by Spring they are not null.

### Common Bug Patterns

Below are concise, human-readable rules you can apply regardless of which static analysis tool you use. If you run Sonar/SonarLint, the IDE will show the matching rule and location — direct Sonar connections are preferred and should override these rules.

- Resource management — Always close resources (files, sockets, streams). Use try-with-resources where possible so resources are closed automatically.
- Equality checks — Compare object equality with `.equals()` or `Objects.equals(...)` rather than `==` for non-primitives; this avoids reference-equality bugs.
- Redundant casts — Remove unnecessary casts; prefer correct generic typing and let the compiler infer types where possible.
- Reachable conditions — Avoid conditional expressions that are always true or false; they indicate bugs or dead code and should be corrected.

For contributors who *do* use Sonar or SonarLint: the IDE/scan will show the specific rule key (for example, S2095 for resource leaks) and the affected file/line. Use that information to navigate to the exact location, then apply the recommended remediation.

### Common Code Smells

These patterns are phrased for humans; they map cleanly to checks in Sonar, SpotBugs, PMD, or Checkstyle but do not require those tools to be useful.

- Parameter count — Keep method parameter lists short. If a method needs many params, consider grouping into a value object or using the builder pattern.
- Method size — Keep methods focused and small. Extract helper methods to improve readability and testability.
- Cognitive complexity — Reduce nested conditionals and heavy branching by extracting methods, using polymorphism, or applying the Strategy pattern.
- Duplicated literals — Extract repeated strings and numbers into named constants or enums to reduce errors and ease changes.
- Dead code — Remove unused variables and assignments. They confuse readers and can hide bugs.
- Magic numbers — Replace numeric literals with named constants that explain intent (e.g., MAX_RETRIES).

If you run a static analyzer like Sonar or SonarLint — direct Sonar connections are preferred and should override this ruleset. Sonar rule keys are useful for automation and suppression, but they are not required in day-to-day developer guidance.

## Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- If the project uses Maven, run `mvn clean install`.
- If the project uses Gradle, run `./gradlew build` (or `gradlew.bat build` on Windows).
- Ensure all tests pass as part of the build.