# ADR-008: Dedicated LLM-Based Location Extraction for Interpellations

**Status:** Accepted
**Date:** 2026-09-17

## Context

`EntityRecognitionPort` (Azure AI Language `analyze-text` NER + Entity Linking, see
`AzureLanguageEntityRecognitionAdapter`) recognizes generic `Person`/`Organization`/`Location`
entities in interpellation body text. Its `Location` category is general-purpose: it does not
reliably normalize inflected Polish place names (e.g. "w Białymstoku", "do Poznania", "z
Gdańska", "w Łodzi") to a single canonical form, filter out non-Polish or ambiguous mentions, or
expose geographic metadata (voivodeship/county) — all needed to present a clean, deduplicated
"Miejscowości" line in the Telegram owner notification for new interpellations.

## Decision

Introduce a separate, geography-specific port instead of extending `EntityRecognitionPort`:

- `LocationExtractionPort#extractLocations(String text): List<LocationCandidate>`
- `LocationCandidate(rawMention, canonicalName, province, county, latitude, longitude, confidence)`

The adapter, `FoundryLocationExtractionAdapter`, calls an LLM model deployment in Azure AI
Foundry (Azure OpenAI-compatible chat completions REST API) with a dedicated extraction prompt
(`.github/prompts/plan-location-extraction3.prompt.md`) that instructs the model to:
- extract only real Polish localities,
- normalize inflected mentions to their canonical name,
- ignore persons/organizations/institutions,
- return a low confidence (or omit) for uncertain matches,
- return strict JSON matching the `LocationCandidate` schema.

The adapter drops candidates below a minimum confidence threshold (precision over recall) and is
disabled — returning an empty list — when `AZURE_FOUNDRY_LOCATION_ENDPOINT`,
`AZURE_FOUNDRY_LOCATION_KEY`, or `AZURE_FOUNDRY_LOCATION_DEPLOYMENT` are not configured. This is a
model used purely for extraction (no agent/tooling is provisioned).

`InterpellationEntitySummaryPresenter#present(RecognizedEntities, List<LocationCandidate>)`
renders `LocationCandidate` results (canonical name plus province when known) for "Miejscowości"
when present, and falls back to `EntityRecognitionPort`'s general `Location` mentions otherwise
(disabled adapter, model failure, or no candidates above the confidence threshold).
`TermSnapshotReconcilerEntity` calls both ports and passes both results to the presenter, keeping
`EntityRecognitionPort` as the fallback rather than replacing it.

Terraform provisions the Foundry/OpenAI resource and model deployment
(`azurerm_cognitive_account.foundry_location`, `azurerm_cognitive_deployment.location_extraction`)
alongside the existing `azurerm_cognitive_account.language` resource, with the API key stored in
Key Vault and surfaced to the Function App via the same `@Microsoft.KeyVault(...)` app-setting
pattern used for `AZURE_LANGUAGE_KEY`.

## Consequences

**Positive:**
- Persons/organizations recognition (`EntityRecognitionPort`) stays decoupled from
  geography-specific normalization concerns.
- Canonical Polish place names and provinces make the Telegram summary more useful and less
  noisy than raw NER `Location` mentions.
- Graceful degradation: if the Foundry deployment is unavailable or misconfigured, the summary
  still shows locations via the existing NER fallback rather than an empty line.

**Negative:**
- One additional Azure AI Foundry/OpenAI resource + model deployment to provision, monitor, and
  pay for.
- LLM-based extraction is probabilistic; the confidence threshold and prompt wording require
  periodic review against real interpellation text to keep precision high.

**Risks:**
- Prompt or model-version changes can shift output shape/quality; the adapter validates and
  discards malformed JSON defensively rather than propagating parsing failures.
