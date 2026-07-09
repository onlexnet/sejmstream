# ADR-006: PostgreSQL with Liquibase Migrations

**Status:** Accepted  
**Date:** 2025-01-15

## Context

SejmStream needs persistent storage for:
1. Collected daily digest items (6 data types, keyed by date + type + item_key)
2. Interpellation publish state (retry tracking, deduplication)
3. Publish audit log (what was posted and when)

Requirements: upsert support (collect may re-process same items), reliable state tracking, schema evolution over time.

## Decision

Use **PostgreSQL** with **Liquibase** for schema management:

### Database
- External PostgreSQL instance (not managed in Terraform — likely Azure Database for PostgreSQL or external provider)
- Connection via JDBC, credentials in Key Vault (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)

### Schema Management
- Liquibase changelogs in `src/main/resources/db/changelog/`
- Master changelog: `db.changelog-master.yaml` includes versioned change files
- Migrations run on application startup (Spring Boot auto-configuration)
- Naming: `NNN-description.yaml` (e.g., `002-create-sejm-daily-digest-tables.yaml`)

### Key Patterns
- **Upsert** via `INSERT ... ON CONFLICT (collection_date, data_type, item_key) DO UPDATE` — idempotent collection
- **Claim** via `INSERT ... ON CONFLICT (term_num, interpellation_num) DO UPDATE SET status = 'QUEUED' WHERE status != 'PUBLISHED'` — prevents re-publishing
- **JdbcTemplate** for data access (no JPA/Hibernate — simpler for this use case)

### Tables
| Table | Purpose |
|-------|---------|
| `sejm_daily_digest_item` | Collected items (JSON + metadata) |
| `sejm_interpellation_publish_state` | Retry state machine |
| `sejm_publish_log` | Audit trail of Facebook posts |

## Consequences

**Positive:**
- Liquibase provides repeatable, version-controlled schema evolution
- Upsert pattern makes collection idempotent (safe to re-run)
- PostgreSQL `ON CONFLICT` is atomic — no race conditions
- JdbcTemplate is simpler and more performant than JPA for this use case
- Full audit trail of all operations

**Negative:**
- External database requires separate provisioning and management
- Liquibase migrations run on every cold start (adds ~1-2s)
- JdbcTemplate requires manual SQL (no query generation)
- No compile-time SQL validation

**Risks:**
- Database connection pool exhaustion under high concurrency (mitigated by Flex Consumption max instances limit)
- Schema migration failures block application startup (mitigated by testing migrations in CI)
