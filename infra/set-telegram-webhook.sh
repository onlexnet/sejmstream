#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ROUTE="${1:-api/telegram/webhook}"
ROUTE="${ROUTE#/}"

HOSTNAME="$(terraform output -raw function_app_default_hostname 2>/dev/null || true)"
if [[ -z "$HOSTNAME" ]]; then
  echo "Could not read Terraform output 'function_app_default_hostname'." >&2
  echo "Run this from the infra folder with initialized Terraform state access." >&2
  exit 1
fi

WEBHOOK_URL="https://${HOSTNAME}/${ROUTE}"
TOKEN="${TELEGRAM_BOT_TOKEN:-${TF_VAR_telegram_bot_token:-}}"

if [[ -z "$TOKEN" ]]; then
  KV_NAME="$(terraform output -raw key_vault_name 2>/dev/null || true)"
  if [[ -n "$KV_NAME" ]] && command -v az >/dev/null 2>&1; then
    TOKEN="$(az keyvault secret show \
      --vault-name "$KV_NAME" \
      --name telegram-bot-token \
      --query value -o tsv 2>/dev/null || true)"
  fi
fi

if [[ -z "$TOKEN" ]]; then
  echo "Telegram bot token not found." >&2
  echo "Set TELEGRAM_BOT_TOKEN or TF_VAR_telegram_bot_token, or login to Azure so the script can read Key Vault secret 'telegram-bot-token'." >&2
  exit 1
fi

echo "Setting Telegram webhook to: $WEBHOOK_URL"
set_webhook_response="$(curl -sS -X POST "https://api.telegram.org/bot${TOKEN}/setWebhook" \
  --data-urlencode "url=${WEBHOOK_URL}")"
echo "$set_webhook_response"

echo "Current webhook info:"
get_webhook_response="$(curl -sS "https://api.telegram.org/bot${TOKEN}/getWebhookInfo")"
echo "$get_webhook_response"
