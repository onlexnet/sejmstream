#!/usr/bin/env bash

set -euo pipefail

mkdir -p /home/worker/.ssh /home/worker/.terraform.d /home/worker/.azure

# Ensure home and tool directories are writable by the non-root devcontainer user.
sudo chown worker:worker /home/worker
sudo chown -R worker:worker /home/worker/.ssh /home/worker/.terraform.d /home/worker/.azure

chmod 700 /home/worker/.ssh /home/worker/.terraform.d

if ! command -v mvn >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y maven
fi

bash /sejmstream/.devcontainer/scripts/azure-init.sh