# SejmStream Projekt
Facebook: [facebook.com/SejmAktualnosci](https://www.facebook.com/SejmAktualnosci)


## Key Vault configuration

Use runtime env:

- `AZURE_KEYVAULT_ENABLED=true`
- `AZURE_KEYVAULT_ENDPOINT=<vault-uri>` from Terraform output `key_vault_uri`.

In devcontainer this is set automatically at startup by `.devcontainer/scripts/azure-init.sh` (saved to `/home/worker/.sejmstream-env`).

Vault URI is usually not a secret; secrets are values stored inside Key Vault.

### Local run (recommended)

This repository is now a Maven monorepo with app module in `fun_sejmlive`.

Run from monorepo root:

- `mvn -pl fun_sejmlive -am spring-boot:run`

Run from module directory:

- `cd fun_sejmlive && mvn spring-boot:run`

Build and test from monorepo root:

- `mvn -pl fun_sejmlive -am test`

## Useful links
- [How to create a Facebook access token](https://www.youtube.com/watch?v=RMLcmDdOSxw)

