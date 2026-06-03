# SejmStream Projekt
Facebook: [facebook.com/SejmAktualnosci](https://www.facebook.com/SejmAktualnosci)


## Key Vault configuration

Use runtime env:

- `AZURE_KEYVAULT_ENABLED=true`
- `AZURE_KEYVAULT_ENDPOINT=<vault-uri>` from Terraform output `key_vault_uri`.

In devcontainer this is set automatically at startup by `.devcontainer/scripts/azure-init.sh` (saved to `/home/worker/.sejmstream-env`).

Vault URI is usually not a secret; secrets are values stored inside Key Vault.

### Local run (recommended)

Start app directly:

- `mvn spring-boot:run`

## Useful links
- [How to create a Facebook access token](https://www.youtube.com/watch?v=RMLcmDdOSxw)

