# ADR-004: Telegram Bot as Admin Interface

**Status:** Accepted  
**Date:** 2025-01-15

## Context

The system needs an operational interface for:
- Triggering manual collection/publishing outside scheduled times
- Checking system status and current Sejm term data
- Viewing available commands

A full web UI would be over-engineered for a single admin. SSH access to Azure Functions is not available. Azure Portal is too heavy for quick operations.

## Decision

Use a **Telegram Bot** as the admin interface:

- **Webhook-based**: Telegram delivers messages to `POST /api/telegram/webhook`
- **Authorization**: Chat ID allowlist (`TELEGRAM_ALLOWED_CHAT_ID` in Key Vault) — only configured chat IDs can execute commands
- **Commands**: `/help`, `/data`, `/collect`, `/publish`
- **Response**: Bot replies with operation results in the same chat

### Architecture
- `TelegramBotFunctions` (inbound adapter) receives webhooks
- `TelegramAdminActionParser` extracts command from message text
- `AdminUseCase` authorizes and dispatches to appropriate use case
- `TelegramAdminOutcomePresenter` formats typed outcomes for display
- `TelegramNotifier` (outbound adapter) sends response via Bot API

## Consequences

**Positive:**
- Zero infrastructure cost (no web server, no UI hosting)
- Mobile-friendly: admin can operate from phone
- Push notifications for responses
- Simple authorization via chat ID
- Natural language-like command interface

**Negative:**
- Single point of failure: if Telegram is down, no admin access
- Limited UI capabilities (text only, no dashboards)
- Webhook URL must be registered with Telegram (see `infra/set-telegram-webhook.sh`)
- Chat ID authorization is coarse-grained (all-or-nothing)

**Risks:**
- Telegram API changes could break the bot (mitigated by using stable Bot API)
- Webhook URL exposure requires HTTPS (provided by Azure Functions)
