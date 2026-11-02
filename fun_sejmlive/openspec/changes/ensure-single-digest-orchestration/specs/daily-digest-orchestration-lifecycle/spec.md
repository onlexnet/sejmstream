# Spec Delta

## Purpose

Keep daily-digest collection serialized while recovering automatically from confirmed orchestration failures, including failures that cannot deliver a notification.

## ADDED Requirements

### Requirement: Single owned loading orchestration

The system SHALL own at most one nonterminal daily-digest loading orchestration, counting future scheduled starts as owned. Collection requests received while an instance is owned MUST NOT create another instance or accumulate extra runs.

#### Scenario: First request starts collection

- **WHEN** a collection request arrives in the idle lifecycle with no instance owned and no recovery block
- **THEN** exactly one loading orchestration is scheduled and its identity is durably retained

#### Scenario: Requests arrive during an owned run

- **WHEN** timer, HTTP, or repeated collection requests arrive while an instance is pending or running
- **THEN** no additional loading orchestration is scheduled

#### Scenario: Requests arrive during a delayed replacement

- **WHEN** a replacement is scheduled to start in the future and another collection request arrives
- **THEN** the scheduled replacement remains the sole owned instance and no additional start is scheduled

### Requirement: Correlated terminal reconciliation

The system SHALL correlate notifications and runtime observations with the currently owned instance. Notifications alone MUST NOT release ownership. Only a matching confirmed terminal runtime observation SHALL release that ownership, and each instance's terminal outcome MUST be applied at most once.

#### Scenario: Completion signal precedes runtime completion

- **WHEN** the current instance signals completion but its observed runtime status is still running
- **THEN** ownership is retained until a subsequent observation confirms a terminal state

#### Scenario: Failure signal precedes runtime failure

- **WHEN** the current instance signals failure but runtime failure is not yet confirmed
- **THEN** no replacement is scheduled until failure is confirmed

#### Scenario: Old notification or observation arrives

- **WHEN** a notification or runtime observation names an instance other than the current owner
- **THEN** current ownership and retry history remain unchanged and no start is scheduled

#### Scenario: Failure is reported more than once

- **WHEN** duplicate matching failure observations or signals are delivered
- **THEN** at most one replacement is scheduled for that failed instance and the retry budget is consumed only once

### Requirement: One-minute runtime observation

The system SHALL schedule a runtime status check every minute for the owned instance, independently of collection requests and orchestrator notifications. Nonterminal status, missing metadata, unknown status, or a failed query MUST NOT be interpreted as failure or authorize a replacement. Observation errors SHALL be reported with the affected instance identity.

#### Scenario: Failure notification never arrives

- **WHEN** an owned instance reaches failed runtime status without delivering its failure notification
- **THEN** a subsequent one-minute status check reports its failure and enables the bounded recovery policy

#### Scenario: Completion notification never arrives

- **WHEN** an owned instance completes successfully without delivering a completion notification
- **THEN** a subsequent one-minute status check releases ownership and resets failure recovery history

#### Scenario: Runtime cannot confirm termination

- **WHEN** the owned instance is pending, running, suspended, transitioning, absent from metadata, or unqueryable
- **THEN** the system retains ownership, does not schedule another loading orchestration, and continues subsequent checks

### Requirement: Bounded automatic failure recovery

After confirmed runtime failure, the system SHALL automatically schedule up to five replacement orchestrations, delayed respectively by 1, 2, 4, 8, and 15 minutes from each failure's acceptance. Retry progress and scheduled ownership MUST survive restarts. Recovery SHALL apply regardless of the failure message, and success SHALL reset retry progress.

#### Scenario: Initial orchestration fails

- **WHEN** runtime failure is confirmed for the initial loading orchestration
- **THEN** the first replacement is scheduled no earlier than one minute after that failure is accepted and becomes the sole owned instance

#### Scenario: Replacements repeatedly fail

- **WHEN** the first through fourth replacement orchestrations each fail
- **THEN** their successors are scheduled after delays of 2, 4, 8, and 15 minutes respectively

#### Scenario: Host restarts during backoff

- **WHEN** the host restarts with a replacement already scheduled
- **THEN** the same scheduled instance and retry ordinal remain owned and no duplicate replacement is created

#### Scenario: Recovery succeeds

- **WHEN** a replacement is confirmed successfully completed
- **THEN** ownership is released, retry progress is reset, and no further replacement is automatically scheduled

### Requirement: Retry exhaustion requires explicit recovery

If the fifth replacement fails, the system SHALL stop automatic starts, durably record exhaustion, and report it operationally. Ordinary timer and HTTP collection requests MUST NOT reset exhaustion. An authorized explicit administrator recovery SHALL start a fresh cycle only when no nonterminal instance remains owned.

#### Scenario: Sixth total run fails

- **WHEN** the fifth replacement after the original run is confirmed failed
- **THEN** no sixth replacement is scheduled and exhaustion is retained across restarts

#### Scenario: Collection timer fires after exhaustion

- **WHEN** a timer or ordinary HTTP collection request arrives after exhaustion
- **THEN** no orchestration is started and exhaustion remains in effect

#### Scenario: Administrator recovers an exhausted cycle

- **WHEN** an authorized administrator explicitly requests recovery after exhaustion with no nonterminal owner
- **THEN** the system clears exhaustion and starts one new initial run with a fresh five-replacement budget

### Requirement: Manual recovery preserves single ownership

Manual recovery SHALL NOT bypass ownership, overlap an existing run, or shorten an already scheduled replacement's delay. If an instance remains owned, recovery SHALL request reconciliation instead of force-starting another instance.

#### Scenario: Administrator recovers a still-running instance

- **WHEN** an authorized recovery command arrives while the owned instance is running or its termination cannot be confirmed
- **THEN** no additional orchestration is started and ownership is retained pending reconciliation

#### Scenario: Administrator recovers a pending replacement

- **WHEN** an authorized recovery command arrives while a delayed replacement is pending
- **THEN** that replacement retains its identity and scheduled start time

### Requirement: Fresh-state deployment and unsupported state rejection

The system SHALL initialize idle only when coordinator state is absent. Legacy boolean-only state, unsupported versions, and invalid persisted payloads MUST fail explicitly without scheduling a run. Deployment SHALL target fresh coordinator state with no preexisting collection runs or queued legacy commands. Data migration and adoption are outside this capability.

#### Scenario: Fresh coordinator receives its first request

- **WHEN** a collection request arrives with no persisted coordinator state in a fresh target environment
- **THEN** the coordinator initializes idle and schedules exactly one initial loading orchestration

#### Scenario: Unsupported persisted state is encountered

- **WHEN** a legacy boolean-only payload, unsupported version, or invalid lifecycle is loaded
- **THEN** the operation fails explicitly, persisted state is not reset to idle, and no orchestration is scheduled
