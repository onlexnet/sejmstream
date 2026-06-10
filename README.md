# SejmStream Projekt
Facebook: [facebook.com/SejmAktualnosci](https://www.facebook.com/SejmAktualnosci)


## Key Vault configuration

Use runtime env:

- `AZURE_KEYVAULT_ENABLED=true`
- `AZURE_KEYVAULT_ENDPOINT=<vault-uri>` from Terraform output `key_vault_uri`.

In devcontainer this is set automatically at startup by `.devcontainer/scripts/azure-init.sh` (saved to `/home/worker/.sejmstream-env`).

Vault URI is usually not a secret; secrets are values stored inside Key Vault.

### Local run (recommended)

This repository is a Maven monorepo with modules `fun_sejmlive` and `fun_sejmlive`.

Run `fun_sejmlive` from monorepo root:

- `mvn -pl fun_sejmlive -am spring-boot:run`

Run `fun_sejmlive` from module directory:

- `cd fun_sejmlive && mvn spring-boot:run`

Build and test `fun_sejmlive` from monorepo root:

- `mvn -pl fun_sejmlive -am test`

Build and test `fun_sejmlive` from monorepo root:

- `mvn -B -q -pl fun_sejmlive -am test`
- `mvn -B -q -pl fun_sejmlive -am -DskipTests package`

`fun_sejmlive` packaging output is generated under:

- `fun_sejmlive/target/azure-functions/sejmstream-fun-sejmlive-local/`

## Useful links
- [How to create a Facebook access token](https://www.youtube.com/watch?v=RMLcmDdOSxw)

