# Domain Model

## Entities (Persistent, with Identity)

### SejmDailyDigestItem
Represents a single collected piece of parliamentary data for a given day.

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Auto-increment PK |
| collectionDate | LocalDate | Date the data refers to |
| dataType | String | Category of parliamentary activity (see DataType) |
| itemKey | String | Unique identifier within type+date |
| itemTitle | String | Human-readable title |
| itemJson | String | Full serialized record (JSON) |
| collectedAt | Instant | When item was stored |

**Uniqueness**: `(collectionDate, dataType, itemKey)`

### InterpellationPublishState
Tracks the publishing lifecycle of an individual interpellation post.

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Auto-increment PK |
| termNum | int | Sejm term number |
| interpellationNum | int | Interpellation number within term |
| domainMessageId | String | Unique message identifier (max 140 chars) |
| collectionDate | LocalDate | When collected |
| interpellationTitle | String | Title text |
| status | PublishStatus | Current state in lifecycle |
| attempt | int | Current attempt number |
| firstQueuedAt | Instant | When first enqueued |
| lastAttemptAt | Instant | Last processing attempt |
| publishedAt | Instant | When successfully published |
| facebookPostMessage | String | Formatted post text |
| lastError | String | Last failure reason |
| createdAt | Instant | Record creation time |
| updatedAt | Instant | Last modification time |
| lastKnownReplyCount | int | Reply count last recorded (default 0) |
| replyNotificationPublishedAt | Instant | When reply notification was last published (nullable) |

**Uniqueness**: `(termNum, interpellationNum)`, `(domainMessageId)`

### PublishLog
Audit record of daily digest publications.

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Auto-increment PK |
| publishDate | LocalDate | Date of digest |
| publishedAt | Instant | When posted |
| postMessage | String | Full post text |
| success | boolean | Whether publish succeeded |
| errorMessage | String | Failure reason if any |

---

## Value Objects (Immutable Records)

### Parliamentary Data (from Sejm API)

```java
record VotingItem(LocalDateTime date, int sitting, int votingNumber,
    String topic, int yes, int no, int abstain, int totalVoted, int notParticipating)

record CommitteeSittingItem(String code, LocalDate date, int num,
    String agenda, String status, String room)

record PrintItem(String number, String title, LocalDateTime changeDate, String deliveryDate)

record InterpellationItem(int num, String title, List<String> to,
    String sentDate, String lastModified, List<ReplyItem> replies)

sealed interface ReplyItem permits ReplyItem.ActualReply, ReplyItem.Prolongation
  record ActualReply(String key, ReplyFrom from, LocalDate receiptDate) implements ReplyItem
  record Prolongation() implements ReplyItem  // ministry requested deadline extension, no document

record WrittenQuestionItem(int num, String title, List<String> to,
    String sentDate, String lastModified)

record BillItem(String number, String title, String dateOfReceipt,
    String submissionType, String status)

record SejmTerm(boolean current, LocalDate from, int num, SejmPrints prints, LocalDate to)
record SejmPrints(int count, LocalDateTime lastChanged, String link)
```

### Queue Message

```java
record InterpellationPublishQueueMessage(
    String domainMessageId,
    int termNum,
    int interpellationNum,
    String title,
    List<String> recipients,
    @Nullable String sentDate,
    int attempt,
    Instant firstQueuedAt,
    @Nullable String lastError) {

    InterpellationPublishQueueMessage withAttempt(int nextAttempt) { ... }
    InterpellationPublishQueueMessage withLastError(String error) { ... }
}
```

### Telegram Models

```java
record TelegramUpdate(long updateId, TelegramMessage message)
record TelegramMessage(long messageId, TelegramChat chat, String text)
record TelegramChat(long id, String type)
```

### Admin Request

```java
record AdminCommandRequest(
    String requestId,
    Instant requestedAt,
    AdminActor actor,
    AdminAction action,
    Map<String, String> metadata)
```

---

## Enums & Sealed Types

### DataType (CHECK constraint values)
```
VOTING | COMMITTEE_SITTING | PRINT | INTERPELLATION | WRITTEN_QUESTION | BILL
```

### PublishStatus
```
QUEUED | PROCESSING | RETRY_SCHEDULED | PUBLISHED | DEAD_LETTER |
PUBLISH_CONFIRMATION_PENDING | QUEUE_ENQUEUE_FAILED
```

### AdminAction (sealed interface)
```
AdminAction (sealed)
├── Noop          (enum constant)
├── Help          (enum constant)
├── Data          (enum constant)
├── Collect       (enum constant)
├── Publish       (enum constant)
└── Unknown       (record: String command)
```

### AdminActor (sealed interface)
```
AdminActor (sealed)
└── ExternalActor (record: String externalId)
```

---

## Sealed Outcome Types

### AdminOutcome (sealed, multi-level hierarchy)
```
AdminOutcome (sealed)
├── ImmediateReply (sealed)
│   ├── BusinessImmediateReply (sealed)
│   │   ├── Unauthorized
│   │   ├── HelpOverview
│   │   ├── DataEmpty
│   │   ├── DataSummary(termNum, from, to, termCount)
│   │   ├── CollectTermMissing
│   │   ├── CollectSuccess(date, termNum, total, votings, committeeSittings, prints, interpellations, writtenQuestions, bills)
│   │   ├── PublishAlreadyDone(date)
│   │   ├── PublishNoData(date)
│   │   ├── PublishSuccess(date)
│   │   └── UnknownAction(command)
│   └── TechnicalImmediateReply (sealed)
│       ├── CollectFailure(reason)
│       └── PublishFailure(reason)
├── BusinessOutcome (sealed)
│   ├── NoReply (sealed)
│   │   └── NoopIgnored
│   └── DeferredReply (sealed)
│       └── ActionDeferred(correlationId)
└── TechnicalOutcome (sealed)
    └── TechnicalImmediateReply (see above)

Supporting enums:
├── DeliveryPolicy: {NO_REPLY, IMMEDIATE_REPLY, DEFERRED_REPLY}
└── OutcomeCategory: {BUSINESS, TECHNICAL}

Default method: deliveryPolicy() → switches on NoReply/ImmediateReply/DeferredReply
```

### CollectDailyDigestOutcome (sealed)
```
CollectDailyDigestOutcome (sealed)
├── TermMissing
├── Collected(...)
└── Failed(reason)
```

### PublishDailyDigestOutcome (sealed)
```
PublishDailyDigestOutcome (sealed)
├── Published(postMessage)
├── SkippedAlreadyPublished(date)
├── SkippedNoDigest(date)
└── Failed(error)
```

### ProcessInterpellationPublishOutcome (sealed)
```
ProcessInterpellationPublishOutcome (sealed)
├── Published(domainMessageId)
├── PublishConfirmationPending(domainMessageId, error)
├── RetryScheduled(attempt, delay)
├── DeadLettered(domainMessageId, lastError)
└── SkippedAlreadyPublished(domainMessageId)
```

---

## Domain Relationships

```
SejmDailyDigestItem ──collects──> VotingItem | CommitteeSittingItem | PrintItem | ...
SejmDailyDigestItem ──triggers──> InterpellationPublishQueueMessage (for INTERPELLATION type)
InterpellationPublishQueueMessage ──processed by──> InterpellationPublishState
InterpellationPublishState ──audited in──> PublishLog (indirectly via Facebook post)
```
