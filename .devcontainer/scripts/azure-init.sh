#!/usr/bin/env bash
set -u

ENV_FILE="/home/worker/.sejmstream-env"
SHELL_SOURCE_LINE='[ -f /home/worker/.sejmstream-env ] && source /home/worker/.sejmstream-env'

if ! command -v az >/dev/null 2>&1; then
  echo "[devcontainer] Azure CLI is not installed in this container. Rebuild dev container."
  exit 0
fi

subscription="${AZURE_SUBSCRIPTION_NAME:-sejmstream-dev}"
if [ -z "$subscription" ]; then
  exit 0
fi

if ! az account show >/dev/null 2>&1; then
  echo "[devcontainer] Azure CLI is not logged in. Run: az login"
  exit 0
fi

if az account set --subscription "$subscription" >/dev/null 2>&1; then
  echo "[devcontainer] Azure subscription set to: $subscription"
else
  echo "[devcontainer] Failed to set Azure subscription: $subscription"
fi

if command -v terraform >/dev/null 2>&1 && [ -d "/sejmstream/infra" ]; then
  key_vault_endpoint="$(cd /sejmstream/infra && terraform output -raw key_vault_uri 2>/dev/null || true)"

  if [ -n "$key_vault_endpoint" ]; then
    cat >"$ENV_FILE" <<EOF
export AZURE_KEYVAULT_ENABLED=true
export AZURE_KEYVAULT_ENDPOINT="$key_vault_endpoint"
EOF
    echo "[devcontainer] Saved local env to $ENV_FILE"
  else
    echo "[devcontainer] key_vault_uri not available from Terraform output (yet)."
  fi
fi

for rc in /home/worker/.bashrc /home/worker/.profile; do
  if [ -f "$rc" ] && ! grep -Fq "$SHELL_SOURCE_LINE" "$rc"; then
    echo "$SHELL_SOURCE_LINE" >>"$rc"
  fi
done
