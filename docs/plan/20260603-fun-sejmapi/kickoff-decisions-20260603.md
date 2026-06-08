# fun_sejmapi Kickoff Decisions

Date: 2026-06-03
Audience: Developers
Status: Approved kickoff decisions

## Context

This note records the approved architectural decisions for the planned `fun_sejmapi` module in this monorepo.

The current monorepo contains only the Spring Boot module `fun_sejmlive`, and its Maven configuration is set to Java 25. That baseline does not apply to the planned Azure Functions module.

## Approved Decisions

1. Create `fun_sejmapi` as a separate Maven module in the monorepo.
2. Build `fun_sejmapi` as an Azure Functions application using Durable Functions.
3. Keep the kickoff scope as a demo flow with HTTP start and status polling, returning sample data instead of integrating with the Sejm API or a database in this iteration.
4. Add dedicated CI coverage for `fun_sejmapi` rather than folding it into the existing `fun_sejmlive` pipeline.
5. Extend Terraform to provision the Azure resources required by the new Function App and Durable Functions storage state.

## Runtime Constraint

`fun_sejmapi` must target Java 21.

Reason: Azure Functions supports Java 21, 17, 11, and 8. The existing `fun_sejmlive` module currently declares Java 25, but that version must not be copied into `fun_sejmapi`.

## Documentation System No-op

No update was made to `AGENTS.md` or `docs/PRD.yaml` because neither file exists in the current workspace.

If this repo later adopts those documentation anchors, the decisions from this note should be copied into:

- `AGENTS.md` for durable repository conventions such as the Java 21 Azure Functions constraint.
- `docs/PRD.yaml` for product-level scope, acceptance criteria, and architectural decision tracking for `fun_sejmapi`.

## Source Anchors

- Monorepo root currently lists only `fun_sejmlive` as a module.
- `fun_sejmlive` currently declares Java 25 in its Maven properties.
- No `AGENTS.md` file exists in the workspace.
- No `docs/PRD.yaml` file exists in the workspace.