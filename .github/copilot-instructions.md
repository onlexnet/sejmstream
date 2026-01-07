# Copilot Instructions for SejmStream Project

## Project Overview
This is a Spring Boot application that interacts with the Sejm API and Face API to track MP (Member of Parliament) statistics.

## Coding Standards & Best Practices

### Java Style
- use [Java instructions](instructions/java.instructions.md) as the base coding standard
- **Use `var`** for local variable type inference when the type is obvious from the right-hand side
  ```java
  var mpStats = new MpStats();
  var response = restTemplate.getForEntity(url, String.class);
  ```
- Use explicit types when clarity is important (method parameters, return types, fields)
- Follow Java naming conventions: camelCase for variables/methods, PascalCase for classes

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
- Write unit tests for all service logic
- Use meaningful test names that describe the scenario
- Mock external dependencies (APIs, databases) in unit tests
- Use `@SpringBootTest` for integration tests
- Aim for high test coverage on business logic

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
