# ADR-005: OpenAPI-Generated Client for Sejm API

**Status:** Accepted  
**Date:** 2024-12-01

## Context

The Sejm API (`api.sejm.gov.pl`) provides REST endpoints for parliamentary data (votings, interpellations, prints, bills, committees, written questions). The API has multiple endpoints with complex response structures. Manual HTTP client code would be error-prone and hard to maintain as the API evolves.

## Decision

Use **OpenAPI Generator Maven Plugin** to generate a type-safe Java client from the Sejm API OpenAPI specification:

- OpenAPI spec stored in `src/main/resources/openapi/` (version-controlled)
- Generated code placed in `target/generated-sources/sejm-openapi/`
- `DefaultSejmApiClient` (outbound adapter) wraps the generated client, translating API models to domain records defined in `SejmApiClient` port interface
- Domain records (`VotingItem`, `InterpellationItem`, etc.) are independent of generated models

## Consequences

**Positive:**
- Type-safe API calls — compile-time detection of request/response mismatches
- Automatic model generation reduces boilerplate
- Easy to update when API spec changes (regenerate)
- Domain records in the port interface decouple business logic from API structure

**Negative:**
- Generated code is verbose and not human-friendly
- Requires maintaining a local copy of the OpenAPI spec (Sejm API may not publish one officially)
- Build-time code generation adds Maven plugin complexity
- Two model layers (generated + domain records) require mapping code

**Risks:**
- Sejm API may change without updating their spec (mitigated by integration tests)
- Generated client may not handle edge cases in API responses (mitigated by adapter-level error handling)
