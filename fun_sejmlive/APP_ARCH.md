# Application Architecture

This application follows Hexagonal Architecture (Ports and Adapters).

## Principles

- Keep business behavior behind input ports (use case contracts).
- Keep transport and framework concerns in input adapters.
- Keep external integrations behind output ports and output adapters.
- Keep runtime assembly and framework wiring in a composition package.
- Enforce null-safety defaults in the app layer via package-level JSpecify annotations.

## Building Blocks and Locations

### Application Core

- `src/main/java/onlexnet/app`
	- Application module boundary and app-level defaults.
- `src/main/java/onlexnet/app/ports/in`
	- Input port contracts exposed by the domain/application layer.
- `src/main/java/onlexnet/app/usecases`
	- Use case implementations orchestrating business behavior.
- `src/main/java/onlexnet/app/ports/out`
	- Output port contracts required by use cases.
- `src/main/java/onlexnet/app/**/package-info.java`
	- Nullness defaults (`@NullMarked`) and package-level constraints.

### Infrastructure Adapters

- `src/main/java/onlexnet/infra/adapters/in`
	- Input adapters for inbound channels (HTTP, timers, webhooks, triggers).
	- Responsible for parsing inbound payloads and delegating to input ports.
- `src/main/java/onlexnet/infra/adapters/out`
	- Output adapter implementations for external APIs, persistence, and publishing.
	- Responsible for translating output port operations to infrastructure calls.

### Legacy Package (Obsolete)

- `src/main/java/onlexnet/sejmapi`
	- Transitional legacy location.
	- Marked as obsolete and scheduled for migration.
	- Existing runtime/bootstrap and older infrastructure code currently still located here.
- `src/main/java/onlexnet/sejmapi/telegram`
	- Transitional Telegram transport code in legacy namespace.

### Target State

- Long-term structure should contain only:
	- `src/main/java/onlexnet/app`
	- `src/main/java/onlexnet/infra/adapters/in`
	- `src/main/java/onlexnet/infra/adapters/out`
- Any code under `src/main/java/onlexnet/sejmapi` is considered technical debt to be moved.

## Dependency Rules

- `app/ports/in` does not depend on adapters.
- `app/usecases` depends on `app/ports/in` and `app/ports/out`.
- `infra/adapters/in` depends on `app/ports/in`.
- `infra/adapters/out` depends on `app/ports/out`.
- Legacy package must not receive new business or adapter logic.

## Reusable Architectural Concepts

- Input Port: stable business-facing contract for inbound operations.
- Use Case: business orchestration unit, free of transport details.
- Output Port: contract for external dependencies required by use cases.
- Input Adapter: channel-specific translation from inbound request to use case call.
- Output Adapter: infrastructure-specific translation from port contract to external system.
- Composition Root: framework-specific bean assembly and lifecycle wiring.

## Typical Runtime Flow (Generic)

1. An inbound trigger reaches an input adapter.
2. The adapter maps transport data to an input port call.
3. A use case executes business logic and orchestrates output dependencies.
4. Output ports are fulfilled by output adapters.
5. The input adapter maps the result back to channel-specific response behavior.

For the collect pipeline, runtime coordination now uses two separate durable entities:
- `CollectCoordinator` serializes collect-run requests.
- `SejmTermSnapshot` stores latest per-term snapshot state and dispatches recognized
	events (new/updated interpellations, new questions/prints/bills, term switch)
	after comparing current snapshot with previous state.

## Dynamic C4 Runtime View

For an end-to-end dynamic view of runtime behavior (collect orchestration, daily publish,
interpellation retry/dead-letter, and Telegram admin flow), see:

- `../docs/c4/c4-dynamic-runtime.puml`
