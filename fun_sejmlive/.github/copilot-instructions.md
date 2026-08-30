# Copilot Instructions for SejmStream Project

## Docs-First Execution Policy (Mandatory)

Before any implementation:
- Review relevant docs under docs/ and ../docs/ (domain model, ports/contracts, runbook, ADRs, C4, sequences).
- Treat docs as authoritative for behavior and architecture.

During implementation:
- Keep naming, boundaries, and flow aligned with docs.
- If docs are missing or outdated, update docs in the same change.
- If ADR-level behavior changes, update or add ADR before finalizing code.

For every substantial change, include a brief sync section:
- Docs used
- Code changed
- Docs changed
- Any intentional temporary drift with follow-up action

## Project Overview
This is a Spring Boot application that interacts with the Sejm API and Face API to track MP (Member of Parliament) statistics.

## Coding Standards & Best Practices

### Java Style
- Use [Java instructions](instructions/java.instructions.md) as the base coding standard for Java-specific rules and naming conventions.
- Keep Java code aligned with the repo’s Spring and Azure Functions conventions.
- Do not generate the `final` keyword for method parameters (input params). Use plain parameter declarations.
- Guardrail: do not add `Objects.requireNonNull(...)` for parameters or record components that are already non-null by default under JSpecify (`@NullMarked` package/class context).
- Use explicit null checks only when null is part of the contract (`@Nullable`, `@NullUnmarked`, external payloads/deserialization, framework callbacks, or boundary validation with custom error semantics).

### Spring Boot Patterns
- Use constructor-based dependency injection (avoid `@Autowired` on fields)
- Keep controllers thin - business logic belongs in service classes
- Use `@Transactional` for database operations that modify data
- Leverage Spring's `RestTemplate` or `WebClient` for HTTP calls
- Use `@Configuration` classes for bean definitions

### Database & JPA
- Use Liquibase for database migrations (see `db/changelog/`)
- Follow naming convention: table names in snake_case, Java entities in PascalCase
- Always use `@Entity` with proper JPA annotations
- Repository interfaces should extend `JpaRepository`

### Testing
- Write unit tests for all service logic.
- Mock external dependencies (APIs, databases) in unit tests.
- Use `@AppTest` for integration tests.
- Aim for high test coverage on business logic.
- Name test methods with the `should...` pattern to make intent explicit (for example `shouldSerializeLocalDateAsIsoDateString`).
- Follow the Java naming conventions in [Java instructions](instructions/java.instructions.md) for test method names.

### Error Handling
- Use proper exception handling with try-catch blocks
- Log errors with appropriate context
- Return meaningful error messages
- Don't swallow exceptions silently

### Code Organization
- Keep related functionality together
- One public class per file
- Group imports logically (java.*, javax.*, third-party, project)
- Use package-private access when appropriate

### Documentation
- Add JavaDoc for public APIs and complex methods
- Use inline comments sparingly - code should be self-documenting
- Keep README and documentation up to date

### Performance & Best Practices
- Close resources properly (use try-with-resources)
- Avoid unnecessary object creation in loops
- Use appropriate data structures (List vs Set vs Map)
- Consider pagination for large data sets
- Cache expensive operations when appropriate

### Security
- Never commit sensitive data (API keys, passwords)
- Use environment variables or Spring profiles for configuration
- Validate all external input
- Use prepared statements (JPA does this by default)

## Project-Specific Guidelines

### API Integration
- Face API and Sejm API calls should handle failures gracefully
- Implement retry logic for transient failures
- Log API requests/responses for debugging

### Database
- MP statistics are stored in the `mp_stats` table
- Use the repository pattern for data access
- Keep database queries efficient

### Configuration
- Application properties in `application.properties`
- Use Spring profiles for different environments (dev, prod)

### Azure Functions Structure (Project Convention)
- Keep Azure Function triggers split by responsibility:
  - one entrypoint class per trigger/orchestrator/activity.
  - shared behavior in a dedicated support class (for example `...FunctionSupport`).
- Durable job orchestrations (batch/collect/publish flows) must guarantee a terminal durable runtime state:
  - success path ends with method return (`ExecutionCompleted`),
  - failure path ends with an unhandled exception from orchestrator code (`ExecutionFailed`).
  Avoid patterns that can leave an instance indefinitely `Running` without an explicit long-lived design.
- Durable orchestrators must never catch `com.microsoft.durabletask.interruption.OrchestratorBlockedException`.
  Since it extends `RuntimeException`, do not place blocking `Task.await()` calls inside broad `RuntimeException` or
  `Exception` catches. Keep failure-reporting catches around only post-await processing, or otherwise ensure the blocking
  await can propagate untouched without signaling failure.
- Keep function name constants in a stable shared class (for example `SejmCollectFunctions`) and avoid duplicating literal names in entrypoint classes.
- Prefer composition/delegation from entrypoint classes to support services rather than large multi-function classes.
- For durable entities and similar lifecycle-driven components, do not use nullable lifecycle fields for state/context.
  Model lifecycle explicitly with typed `Uninitialized`/`Initialized` variants.

### Java contract rules
- For Java DTOs, records, sealed hierarchies, and API contracts, follow the JSON round-trip rules in [.github/instructions/java.instructions.md](instructions/java.instructions.md).
- Treat serialization compatibility as part of the public contract whenever data is persisted, queued, or exchanged over HTTP.

### Refactor Verification Rules
- If function entrypoints are split, split tests in parallel into focused suites:
  - contract/annotation tests,
  - trigger behavior tests,
  - orchestrator behavior tests,
  - activity behavior tests,
  - Spring wiring/startup tests.
- Extract reusable fakes and test harnesses into a dedicated test support class.
- Run at least targeted compile and the affected focused test suites before finalizing changes.

## Common Tasks

### Adding a New Entity
1. Create the entity class with JPA annotations
2. Create a repository interface
3. Create a Liquibase changelog for the table
4. Add service layer for business logic
5. Write tests

### Adding a New API Endpoint
1. Add method to API client class (FaceApi, SejmApi)
2. Add corresponding model classes if needed
3. Update service layer to use the new endpoint
4. Add error handling
5. Write tests

## Reminders
- Always run tests before committing
- Keep dependencies up to date
- Follow the existing code style
- Ask for clarification when requirements are unclear
