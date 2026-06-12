# Requirements: Daily Sejm Digest — DB-first Collection + Separate Publishing

## Overview

Add two independent Azure Durable Function workflows to the `fun_sejmlive` module:

1. **Collection** (timer at 11 PM) — fetches today's Sejm activity from 6 API categories, stores each item as JSON in a new `sejm_daily_digest_item` DB table.
2. **Publishing** (timer at 11:30 PM) — reads today's collected items from DB, formats a Polish-language digest, publishes to Facebook.

Reuses the existing Durable Functions pattern from `DemoDurableFunctions`, `RestClient` infra, `JdbcTemplate`, and `FacebookPublisher`.

---

## Phase 1 — Extend SejmApiClient & DefaultSejmApiClient

Status: Implemented

### 1.1 New records in `SejmApiClient.java`

| Record | Fields |
|--------|--------|
| `VotingItem` | `LocalDateTime date, int sitting, int votingNumber, String topic, int yes, int no, int abstain, int totalVoted, int notParticipating` |
| `CommitteeSittingItem` | `String code, LocalDate date, int num, String agenda, String status, String room` |
| `PrintItem` | `String number, String title, LocalDateTime changeDate, String deliveryDate` |
| `InterpellationItem` | `int num, String title, List<String> to, String sentDate, String lastModified` |
| `WrittenQuestionItem` | `int num, String title, List<String> to, String sentDate, String lastModified` |
| `BillItem` | `String number, String title, String dateOfReceipt, String submissionType, String status` |

### 1.2 New methods in `SejmApiClient` interface

| Method | Endpoint |
|--------|----------|
| `List<VotingItem> fetchVotingsForDate(int termNum, LocalDate date)` | `GET /sejm/term{N}/votings/search?dateFrom={date}&dateTo={date}` |
| `List<CommitteeSittingItem> fetchCommitteeSittingsForDate(int termNum, LocalDate date)` | `GET /sejm/term{N}/committees/sittings/{date}` |
| `List<PrintItem> fetchPrintsModifiedSince(int termNum, LocalDate since)` | `GET /sejm/term{N}/prints?sort_by=-lastModified&limit=100` + client-side date filter |
| `List<InterpellationItem> fetchInterpellationsModifiedSince(int termNum, LocalDateTime since)` | `GET /sejm/term{N}/interpellations?modifiedSince={since}&sort_by=-lastModified&limit=100` |
| `List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(int termNum, LocalDateTime since)` | `GET /sejm/term{N}/writtenQuestions?modifiedSince={since}&sort_by=-lastModified&limit=100` |
| `List<BillItem> fetchBillsReceivedSince(int termNum, LocalDate since)` | `GET /sejm/term{N}/bills?dateOfReceiptFrom={since}&sort_by=-dateOfReceipt&limit=100` |

### 1.3 Implement in `DefaultSejmApiClient.java`

- Follow existing `restClient.get().uri(...).retrieve().body(new ParameterizedTypeReference<List<...>>(){})` pattern
- For prints: filter client-side by `changeDate.toLocalDate().isEqual(since) || changeDate.toLocalDate().isAfter(since)`

---

## Phase 2 — Liquibase DB Schema

Status: Implemented

New file: `fun_sejmlive/src/main/resources/db/changelog/changes/002-create-sejm-daily-digest-tables.yaml`

### Table: `sejm_daily_digest_item`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, auto-increment |
| `collection_date` | DATE | NOT NULL |
| `data_type` | VARCHAR(50) | NOT NULL — `VOTING\|COMMITTEE_SITTING\|PRINT\|INTERPELLATION\|WRITTEN_QUESTION\|BILL` |
| `item_key` | VARCHAR(255) | NOT NULL — unique per type |
| `item_title` | VARCHAR(1000) | nullable |
| `item_json` | TEXT | full serialized JSON |
| `collected_at` | TIMESTAMP | NOT NULL DEFAULT NOW() |

- UNIQUE constraint on `(collection_date, data_type, item_key)`
- INDEX on `collection_date`

Item key conventions:
- Voting: `"{sitting}/{votingNumber}"`
- CommitteeSitting: `"{code}/{num}"`
- Print / Interpellation / WrittenQuestion / Bill: `"{number}"` or `"{num}"`

### Table: `sejm_publish_log`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, auto-increment |
| `publish_date` | DATE | NOT NULL |
| `published_at` | TIMESTAMP | NOT NULL |
| `post_message` | TEXT | nullable |
| `success` | BOOLEAN | NOT NULL DEFAULT FALSE |
| `error_message` | VARCHAR(1000) | nullable |

Add `002` include to `db.changelog-master.yaml`.

---

## Phase 3 — Repository & Collect Service

### `SejmDailyDigestRepository` (`@Component`, `onlexnet/sejmapi/`)

Uses `JdbcTemplate`. Methods:
- `upsertItem(LocalDate date, String dataType, String itemKey, String title, String itemJson)` — PostgreSQL `INSERT ... ON CONFLICT (collection_date, data_type, item_key) DO UPDATE SET ...`
- `findByDate(LocalDate date)` → `List<Map<String,Object>>`
- `findByDateAndType(LocalDate date, String dataType)` → `List<Map<String,Object>>`
- `insertPublishLog(LocalDate date, String message, boolean success, String errorMsg)`
- `alreadyPublishedToday(LocalDate date)` → `boolean`

### `SejmCollectService` (`@Component`, `onlexnet/sejmapi/`)

Dependencies: `SejmApiClient`, `SejmDailyDigestRepository`, `ObjectMapper`

Methods (each returns count of items inserted/updated):
- `int collectVotings(int termNum, LocalDate date)`
- `int collectCommitteeSittings(int termNum, LocalDate date)`
- `int collectPrints(int termNum, LocalDate date)`
- `int collectInterpellations(int termNum, LocalDate date)`
- `int collectWrittenQuestions(int termNum, LocalDate date)`
- `int collectBills(int termNum, LocalDate date)`

---

## Phase 4 — Digest Service

### `SejmDigestService` (`@Component`, `onlexnet/sejmapi/`)

Dependencies: `SejmDailyDigestRepository`, `ObjectMapper`

Method: `Optional<String> buildDigest(LocalDate date)`

Logic:
1. Load all items from `sejm_daily_digest_item` for `date` grouped by `data_type`
2. Deserialize JSON back to typed objects
3. Format Polish post — only include sections with data
4. If all sections empty → return `Optional.empty()`
5. Each section truncated to 5 items + "... i N więcej"

**Post format:**
```
🏛️ Dzisiaj w Sejmie ({date}):

📊 GŁOSOWANIA ({count}):
• {topic} — ZA: {yes}, PRZECIW: {no}, WSTRZYM: {abstain}

📋 KOMISJE ({count}):
• {code} ({status}) — {agenda excerpt stripped of HTML}

📄 DRUKI ({count}):
• Nr {number}: {title}

🗣️ INTERPELACJE ({count}):
• {title} → {to joined with ", "}

❓ ZAPYTANIA ({count}):
• {title} → {to joined with ", "}

📝 PROJEKTY ({count}):
• {title}

#SejmStream #Sejm #ParlamentPolski
```

---

## Phase 5 — Collection Durable Functions

New file: `fun_sejmlive/src/main/java/onlexnet/sejmapi/SejmCollectFunctions.java`

Mirrors `DemoDurableFunctions.java` pattern exactly:

| Function | Trigger | Description |
|----------|---------|-------------|
| `SejmApiDemo_CollectTimer` | `@TimerTrigger(schedule = "0 0 23 * * *")` | Starts collect orchestrator daily at 11 PM |
| `SejmApiDemo_CollectStart` | HTTP POST, FUNCTION auth | Manual trigger fallback |
| `SejmApiDemo_CollectOrchestrator` | Durable orchestrator | Calls 6 activities sequentially; returns `CollectResult` |
| `SejmApiDemo_CollectVotings` | Durable activity | Delegates to `SejmCollectService.collectVotings()` |
| `SejmApiDemo_CollectCommittees` | Durable activity | Delegates to `SejmCollectService.collectCommitteeSittings()` |
| `SejmApiDemo_CollectPrints` | Durable activity | Delegates to `SejmCollectService.collectPrints()` |
| `SejmApiDemo_CollectInterpellations` | Durable activity | Delegates to `SejmCollectService.collectInterpellations()` |
| `SejmApiDemo_CollectQuestions` | Durable activity | Delegates to `SejmCollectService.collectWrittenQuestions()` |
| `SejmApiDemo_CollectBills` | Durable activity | Delegates to `SejmCollectService.collectBills()` |

Constructor: `SejmCollectFunctions(SejmCollectService, SejmApiClient)` — injected via `CustomFunctionInstanceInjector`

`CollectResult` record: `Map<String, Integer> countsByType` (one entry per data type)

---

## Phase 6 — Refactor Publishing Functions

In `FacebookPublishingFunctions.java`:
- Remove direct `SejmApiClient` dependency
- Inject `SejmDigestService digestService` and `SejmDailyDigestRepository repository`
- Change timer: `0 0 6 * * *` → `0 30 23 * * *` (11:30 PM — 30 min after collection)
- Remove `buildSummaryMessage()` helper

New `publishHelloMessage()` logic:
1. Check `repository.alreadyPublishedToday(LocalDate.now())` → log and return if already published
2. Call `digestService.buildDigest(LocalDate.now())`
3. If `Optional.empty()` → log "no activity today, skipping"
4. Else → `facebookPublisher.publish(message)`, then `repository.insertPublishLog(date, message, true, null)`
5. On exception → `repository.insertPublishLog(date, null, false, e.getMessage())`; rethrow

---

## Phase 7 — Tests

### Updated: `FacebookPublishingFunctionsTest.java`
- Update timer assertion: `"0 0 6 * * *"` → `"0 30 23 * * *"`
- Update mocks: inject mock `SejmDigestService` returning a test message
- Add test: `alreadyPublishedToday=true` → `facebookPublisher.publish()` NOT called

### New: `SejmCollectFunctionsTest.java`
- Timer trigger scheduled at `"0 0 23 * * *"`
- HTTP trigger function name is `"SejmApiDemo_CollectStart"`

### New: `SejmCollectServiceTest.java`
- Mock `SejmApiClient` + `SejmDailyDigestRepository` + `ObjectMapper`
- Each collect method calls correct API method
- Upserts with correct item key convention per type

### New: `SejmDigestServiceTest.java`
- Items in DB for today → `buildDigest()` returns post containing expected sections
- No items in DB → returns `Optional.empty()`
- Section has > 5 items → truncated with "... i N więcej"

---

## Files to Create/Modify

| Action | File |
|--------|------|
| Modify | `fun_sejmlive/src/main/java/onlexnet/app/ports/out/SejmApiClient.java` |
| Modify | `fun_sejmlive/src/main/java/onlexnet/infra/adapters/out/DefaultSejmApiClient.java` |
| Create | `fun_sejmlive/src/main/resources/db/changelog/changes/002-create-sejm-daily-digest-tables.yaml` |
| Modify | `fun_sejmlive/src/main/resources/db/changelog/db.changelog-master.yaml` |
| Create | `fun_sejmlive/src/main/java/onlexnet/sejmapi/SejmDailyDigestRepository.java` |
| Create | `fun_sejmlive/src/main/java/onlexnet/sejmapi/SejmCollectService.java` |
| Create | `fun_sejmlive/src/main/java/onlexnet/sejmapi/SejmDigestService.java` |
| Create | `fun_sejmlive/src/main/java/onlexnet/sejmapi/SejmCollectFunctions.java` |
| Modify | `fun_sejmlive/src/main/java/onlexnet/sejmapi/FacebookPublishingFunctions.java` |
| Modify | `fun_sejmlive/src/test/java/onlexnet/sejmapi/FacebookPublishingFunctionsTest.java` |
| Create | `fun_sejmlive/src/test/java/onlexnet/sejmapi/SejmCollectFunctionsTest.java` |
| Create | `fun_sejmlive/src/test/java/onlexnet/sejmapi/SejmCollectServiceTest.java` |
| Create | `fun_sejmlive/src/test/java/onlexnet/sejmapi/SejmDigestServiceTest.java` |

---

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Timer schedules | Collect 23:00, Publish 23:30 | 30-min gap ensures collection finishes before publishing |
| Decoupling | Two independent timers | Simpler than orchestrator-to-sub-orchestration chain |
| Storage | Single table with `data_type` + `item_json` | Avoids 6 separate typed tables; flexible for future types |
| Upsert | `INSERT ... ON CONFLICT DO UPDATE` | Collection is idempotent — safe to re-run |
| Publish guard | `alreadyPublishedToday()` check | Prevents double-posting on function retries |
| Prints filter | Client-side date filter | Sejm prints API has no `modifiedSince` parameter |
| Sections | Max 5 items + overflow message | Keeps Facebook post readable |
| Excluded (v1) | Per-MP vote details, video transmissions | Too verbose for a post |

---

## Verification

1. `cd fun_sejmlive && mvn test` passes with no failures
2. POST to `SejmApiDemo_CollectStart` locally → rows appear in `sejm_daily_digest_item`
3. Run collection twice same day → row counts stable (upsert idempotency)
4. POST to publish trigger → Facebook page receives post with sections matching DB rows
5. Collection on a no-activity day → `buildDigest()` returns empty → no FB post → `sejm_publish_log` entry logged
