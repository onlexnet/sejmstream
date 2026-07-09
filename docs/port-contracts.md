# Port Contracts

## Dependency Direction

```
┌─────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER                       │
│                                                              │
│  ┌──────────────┐                      ┌──────────────────┐ │
│  │ Inbound      │                      │ Outbound         │ │
│  │ Adapters     │──────────────────────>│ Adapters         │ │
│  │ (Driving)    │         │            │ (Driven)         │ │
│  └──────────────┘         │            └──────────────────┘ │
│         │                 │                     ▲            │
│         │          depends on             implements         │
│         ▼                 │                     │            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              APPLICATION CORE                         │   │
│  │                                                       │   │
│  │  ┌────────────┐    ┌────────────┐    ┌────────────┐  │   │
│  │  │ Inbound    │    │ Use Cases  │    │ Outbound   │  │   │
│  │  │ Ports      │<───│ (Impls)    │───>│ Ports      │  │   │
│  │  │ (interfaces)│    │            │    │ (interfaces)│  │   │
│  │  └────────────┘    └────────────┘    └────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Inbound Ports (Use Case Interfaces)

### CollectDailyDigestUseCase
```java
public interface CollectDailyDigestUseCase {
    CollectDailyDigestOutcome collect(CollectDailyDigestCommand command);
}
```
**Command**: `CollectDailyDigestCommand(LocalDate date)`  
**Outcome**: `TermMissing` | `Collected(...)` | `Failed(String reason)`

---

### PublishDailyDigestUseCase
```java
public interface PublishDailyDigestUseCase {
    PublishDailyDigestOutcome publish(PublishDailyDigestCommand command);
}
```
**Command**: `PublishDailyDigestCommand(LocalDate date)`  
**Outcome**: `Published(String postMessage)` | `SkippedAlreadyPublished(LocalDate)` | `SkippedNoDigest(LocalDate)` | `Failed(String error)`

---

### ProcessInterpellationPublishUseCase
```java
public interface ProcessInterpellationPublishUseCase {
    ProcessInterpellationPublishOutcome process(ProcessInterpellationPublishCommand command);
}
```
**Command**: `ProcessInterpellationPublishCommand(InterpellationPublishQueueMessage message)`  
**Outcome**: `Published` | `PublishConfirmationPending` | `RetryScheduled` | `DeadLettered` | `SkippedAlreadyPublished`

---

### AdminUseCase
```java
public interface AdminUseCase {
    AdminOutcome handleAdminAction(AdminCommandRequest request);
}
```
**Request**: `AdminCommandRequest(String requestId, Instant requestedAt, AdminActor actor, AdminAction action, Map<String, String> metadata)`  
**Outcome**: Multi-level sealed hierarchy (see domain-model.md for full tree)

---

## Outbound Ports (Driven Interfaces)

### SejmApiClient
```java
public interface SejmApiClient {
    List<SejmTerm> fetchTerms();
    List<VotingItem> fetchVotingsForDate(int termNum, LocalDate date);
    List<CommitteeSittingItem> fetchCommitteeSittingsForDate(int termNum, LocalDate date);
    List<PrintItem> fetchPrintsModifiedSince(int termNum, LocalDate since);
    List<InterpellationItem> fetchInterpellationsModifiedSince(int termNum, LocalDateTime since);
    List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(int termNum, LocalDateTime since);
    List<BillItem> fetchBillsReceivedSince(int termNum, LocalDate since);
}
```
**Adapter**: `DefaultSejmApiClient` (OpenAPI-generated REST client wrapping `api.sejm.gov.pl`)

---

### SejmCollectOperations
```java
public interface SejmCollectOperations {
    int collectVotings(int termNum, LocalDate date);
    int collectCommitteeSittings(int termNum, LocalDate date);
    int collectPrints(int termNum, LocalDate date);
    int collectInterpellations(int termNum, LocalDate date);
    int collectWrittenQuestions(int termNum, LocalDate date);
    int collectBills(int termNum, LocalDate date);
}
```
**Adapter**: `SejmCollectService` (coordinates SejmApiClient → SejmDailyDigestPersistence → Queue for each data type)

---

### FacebookPublisher
```java
public interface FacebookPublisher {
    void publish(String message);
}
```
**Adapter**: `DefaultFacebookPublisher` (RestFB library, uses `FB_TOKEN`)

---

### TelegramNotifier
```java
public interface TelegramNotifier {
    void sendMessage(long chatId, String text);
}
```
**Adapter**: `DefaultTelegramNotifier` (HTTP client, Telegram Bot API)

---

### SejmDailyDigestPersistence
```java
public interface SejmDailyDigestPersistence {
    int upsertItem(LocalDate date, String dataType, String itemKey, String title, String itemJson);
    List<Map<String, Object>> findByDate(LocalDate date);
    List<Map<String, Object>> findByDateAndType(LocalDate date, String dataType);
    int insertPublishLog(LocalDate date, @Nullable String message, boolean success, @Nullable String errorMsg);
    boolean alreadyPublishedToday(LocalDate date);
}
```
**Adapter**: `DefaultSejmDailyDigestPersistence` (JdbcTemplate, PostgreSQL with `INSERT ... ON CONFLICT DO UPDATE`)

---

### InterpellationPublishStatePort
```java
public interface InterpellationPublishStatePort {
    boolean tryCreateQueuedRecord(InterpellationPublishQueueMessage message, LocalDate collectionDate);
    boolean tryClaimForPublish(InterpellationPublishQueueMessage message);
    boolean isPublished(int termNum, int interpellationNum);
    void markPublished(InterpellationPublishQueueMessage message, String facebookPostMessage);
    void markPublishConfirmationPending(InterpellationPublishQueueMessage message, String errorMessage, String facebookPostMessage);
    void markRetryScheduled(InterpellationPublishQueueMessage message, String errorMessage);
    void markEnqueueFailed(InterpellationPublishQueueMessage message, String errorMessage);
    void markDeadLetter(InterpellationPublishQueueMessage message, String errorMessage);
}
```
**Adapter**: JdbcTemplate implementation with `INSERT ... ON CONFLICT` for claim semantics

---

### InterpellationPublishQueuePort
```java
public interface InterpellationPublishQueuePort {
    void enqueue(InterpellationPublishQueueMessage message, Duration visibilityDelay);
    void enqueueDeadLetter(InterpellationPublishQueueMessage message);
}
```
**Adapter**: Azure Storage Queue SDK (`sejm-interpellations-publish`, `sejm-interpellations-publish-deadletter`)

---

### AdminAccessPolicy
```java
public interface AdminAccessPolicy {
    boolean isAllowed(AdminActor actor, AdminAction action);
}
```
**Adapter**: `PropertyAdminAccessPolicy` (compares `ExternalActor.externalId` against `TELEGRAM_ALLOWED_CHAT_ID` config)

---

## Port Contract Rules

1. **Ports are interfaces** — always declared in `onlexnet.app.ports.{in|out}`
2. **Commands are records** — immutable, validated at creation
3. **Outcomes are sealed types** — exhaustive pattern matching by callers
4. **No framework types cross port boundaries** — no Spring, Azure, or JDBC types in signatures
5. **Adapters depend on ports** — never the reverse
6. **One adapter per port** (current state) — multiple implementations possible for testing
7. **AdminOutcome uses delivery policy** — `deliveryPolicy()` method enables callers to decide response behavior without inspecting variant types
