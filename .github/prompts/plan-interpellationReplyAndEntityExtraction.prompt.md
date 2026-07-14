# Plan: Interpellation Reply Tracking + AI Entity Identification

Two independent features addressing your request.

---

## Feature 1: Reply Detection → New Post Publication

**When a ministry replies to an already-published interpellation, publish a new separate Facebook post referencing it.**

### Phase 1A — Domain & Data Model ✅ IMPLEMENTED

1. Extend `InterpellationItem` record with `List<ReplyItem> replies` (new record: `key`, `from`, `receiptDate`)
2. Update `DefaultSejmApiClient.mapInterpellation()` to populate replies (currently dropped)
3. DB migration: add `last_known_reply_count INT DEFAULT 0` and `reply_notification_published_at TIMESTAMP NULL` to `sejm_interpellation_publish_state`
4. Extend `InterpellationPublishStatePort` with reply state update/read methods

### Phase 1B — Queue & Ports *(parallel with 1A)*

5. New record `InterpellationReplyPublishQueueMessage(term, num, title, newReplies)`
6. New port `InterpellationReplyPublishQueuePort`
7. New command `ProcessInterpellationReplyPublishCommand` + sealed outcome — mirror existing pattern

### Phase 1C — Use Case *(depends on 1A + 1B)*

8. New `DefaultProcessInterpellationReplyPublishUseCase`: claim state atomically → publish Facebook post with format:
   > _Odpowiedź na interpelację nr {num} (kadencja {term})_
   > _{title}_
   > _Odpowiedź od: {from}, Data: {receiptDate}_
   > _https://www.sejm.gov.pl/sejm{term}.nsf/interpelacja.xsp?nr={num}&kadencja={term}_
9. New status values: `REPLY_QUEUED → REPLY_PROCESSING → REPLY_PUBLISHED / REPLY_DEAD_LETTER`

### Phase 1D — Collection Update *(depends on 1A)*

10. In `SejmCollectService`: for `PUBLISHED` interpellations, fetch full details via `InterpellationsApi.getInterpellation(term, num)`, compare `replies.size()` vs `last_known_reply_count` → enqueue if new replies detected

### Phase 1E — Infrastructure

11. New `AzureStorageInterpellationReplyPublishQueue` adapter + `InterpellationReplyPublishQueueFunctions` trigger — mirror existing pattern
12. `infra/main.tf`: add `sejm-interpellation-reply-publish` and dead-letter queues + env vars

### Phase 1F — Tests

13. `DefaultProcessInterpellationReplyPublishUseCaseTest` — unit test happy path, retry, dead-letter
14. Update `SejmCollectService` test to verify reply detection and enqueue logic
15. Architecture test (`HexagonalBoundariesTest`) should pass without changes if packages are correct

---

## Feature 2: AI Topic Entity Identification

**After publishing an interpellation, use Azure OpenAI to extract topic/policy entities from its title; store for future channel routing.**

### Phase 2A — ADR + Dependency *(independent)*

1. Create `docs/adr/008-ai-entity-extraction.md`
2. Add `com.azure:azure-ai-openai` dependency to `fun_sejmlive/pom.xml`

### Phase 2B — Domain & Ports *(parallel with 2A)*

3. New `EntityType` enum: `TOPIC, MINISTRY, POLICY_AREA, KEYWORD`
4. New `ExtractedEntity(String name, EntityType type)` record
5. New port `EntityExtractionPort.extractEntities(String title): List<ExtractedEntity>`
6. New port `InterpellationEntityStorePort.storeEntities(term, num, entities)`

### Phase 2C — Persistence *(depends on 2B)*

7. New `InterpellationEntityJpaEntity` + `InterpellationEntityRepository`
8. DB migration: `sejm_interpellation_entity(id, term_num, interpellation_num, entity_name, entity_type, extracted_at)` + index on `(term_num, interpellation_num)`
9. New `JpaInterpellationEntityStoreAdapter`

### Phase 2D — Use Case *(depends on 2B)*

10. `DefaultIdentifyInterpellationEntitiesUseCase`: calls extraction port → calls store port
11. **Trigger**: call from `DefaultProcessInterpellationPublishUseCase` after successful publish (wrapped in try-catch, non-blocking)

### Phase 2E — Azure OpenAI Adapter *(depends on 2B)*

12. `AzureOpenAiEntityExtractionAdapter`: structured JSON prompt → parse `List<ExtractedEntity>`; authenticated via managed identity (`DefaultAzureCredential`)

### Phase 2F — Infrastructure

13. `infra/main.tf`: Azure OpenAI resource + role assignment `Cognitive Services OpenAI User` for function identity + KV secret for endpoint

### Phase 2G — Tests

14. `DefaultIdentifyInterpellationEntitiesUseCaseTest` — mock `EntityExtractionPort` and `InterpellationEntityStorePort`; verify correct wiring
15. `AzureOpenAiEntityExtractionAdapterTest` — unit test prompt construction; mock Azure OpenAI HTTP call

---

## Relevant Files

**Domain model & docs**
- `docs/domain-model.md` — extend with `ReplyItem`, `ExtractedEntity`, `EntityType`
- `docs/port-contracts.md` — add all new ports
- `docs/adr/` — add `008-ai-entity-extraction.md`
- `docs/sequences/seq-interpellation-publish.puml` — extend with reply flow

**Existing files to modify**
- `fun_sejmlive/src/main/java/onlexnet/app/ports/out/InterpellationPublishStatePort.java` — add reply state methods
- `fun_sejmlive/src/main/java/onlexnet/infra/adapters/out/DefaultSejmApiClient.java` — populate replies in mapping
- `fun_sejmlive/src/main/java/onlexnet/infra/adapters/out/SejmCollectService.java` — add reply detection
- `fun_sejmlive/src/main/java/onlexnet/app/usecases/DefaultProcessInterpellationPublishUseCase.java` — call entity identification after publish
- `fun_sejmlive/src/main/resources/application.properties` — add new queue + AI properties
- `infra/main.tf` — add reply queues + Azure OpenAI
- `fun_sejmlive/pom.xml` — add azure-ai-openai dependency

**New files**
- `app.ports.in.interpellation.ProcessInterpellationReplyPublishCommand`
- `app.ports.in.interpellation.ProcessInterpellationReplyPublishOutcome`
- `app.ports.in.interpellation.ProcessInterpellationReplyPublishUseCase`
- `app.ports.in.interpellation.IdentifyInterpellationEntitiesUseCase`
- `app.ports.out.InterpellationReplyPublishQueueMessage`
- `app.ports.out.InterpellationReplyPublishQueuePort`
- `app.ports.out.EntityExtractionPort`
- `app.ports.out.InterpellationEntityStorePort`
- `app.domain.ExtractedEntity`
- `app.domain.EntityType`
- `app.usecases.DefaultProcessInterpellationReplyPublishUseCase`
- `app.usecases.DefaultIdentifyInterpellationEntitiesUseCase`
- `infra.adapters.out.AzureStorageInterpellationReplyPublishQueue`
- `infra.adapters.out.JpaInterpellationEntityStoreAdapter`
- `infra.adapters.out.ai.AzureOpenAiEntityExtractionAdapter`
- `infra.adapters.in.collect.InterpellationReplyPublishQueueFunctions`
- DB migrations: 007-add-reply-state-columns, 008-add-reply-statuses, 009-create-interpellation-entity-table

---

## Verification

1. Run `mvn test` — all existing tests pass; new unit tests pass
2. `HexagonalBoundariesTest` passes — new classes in correct packages
3. Liquibase migrations apply cleanly against PostgreSQL (testcontainers IT)
4. Manual: trigger collect → verify `last_known_reply_count` updates in DB for interpellations with replies
5. Manual: verify new Facebook post appears when reply is detected (staging env)
6. Manual: verify `sejm_interpellation_entity` table populated after publish

---

## Decisions

- **Reply notification = new post** (not Facebook comment), referencing interpellation number and Sejm URL
- **Entities = AI-based topic/keyword classification** (not MP/ministry attribution)
- **Entity-channel mapping is OUT of scope** — just identify and store entities
- **AI extraction on title only** (not full HTML body) to start — reduces complexity
- **Feature 1 and Feature 2 are independent** — can be implemented in parallel
- **Reply status reuses existing DB table** — avoids new table; adds columns to existing one
- **Retry policy for replies** — reuse existing `InterpellationPublishRetryPolicy` pattern

---

## Open Questions / Considerations

1. **Azure OpenAI region/SKU**: needs a GPT-4o deployment in Azure Poland Central or nearest region — coordinate with infra team
2. **Reply check scope**: Checking ALL published interpellations for replies each day could mean many API calls — consider limiting to interpellations published within the last 30 days
3. **`InterpellationItem` location**: Confirm exact package/class before modifying — it may be nested inside the `SejmApiClient` port or the adapter
