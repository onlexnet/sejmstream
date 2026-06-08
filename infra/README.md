# Terraform Cloud execution

Terraform in `infra/` is configured for remote execution in the `onlexnet/sejmstream-dev` Terraform Cloud workspace.

## Required setup

1. Create the `sejmstream-dev` workspace in the `onlexnet` organization.
2. Add the following GitHub Actions repository secret:
   - `TF_API_TOKEN`: Terraform Cloud team or user token with access to the workspace.
3. In the Terraform Cloud workspace, configure Azure authentication. Remote runs cannot use a local `az login`.
4. Add Terraform variables from `terraform.tfvars.example` as workspace variables or `TF_VAR_*` environment variables.

## Azure authentication options

Use one of these workspace configurations.

### Option 1: HCP Terraform dynamic credentials (recommended)

Configure Azure workload identity trust for the `onlexnet/sejmstream-dev` workspace, then add these workspace environment variables:

- `TFC_AZURE_PROVIDER_AUTH=true`
- `TFC_AZURE_RUN_CLIENT_ID=<azure-app-client-id>`
- `ARM_SUBSCRIPTION_ID=<azure-subscription-id>`
- `ARM_TENANT_ID=<azure-tenant-id>`

This avoids storing a long-lived Azure client secret in Terraform Cloud.

### Option 2: Azure service principal secret

Add these workspace environment variables:

- `ARM_CLIENT_ID`
- `ARM_CLIENT_SECRET`
- `ARM_SUBSCRIPTION_ID`
- `ARM_TENANT_ID`

## Troubleshooting

- If a remote run fails with `unable to build authorizer for Resource Manager API: no Authorizer could be configured`, the workspace is missing valid Azure authentication environment variables.
- If a remote run fails with `exec: "az": executable file not found in $PATH`, the workspace does not have Terraform Cloud-compatible Azure credentials configured yet.
- The `azurerm` provider is set with `use_cli = false`, so remote runs will not try to authenticate through Azure CLI.

## Workflow behavior

- Pull requests run `terraform fmt`, `terraform validate`, and a remote `terraform plan`.
- Pushes to `main` run `terraform fmt`, `terraform validate`, and a remote `terraform apply`.
- The `Terraform Cloud` workflow can also be started manually to run either a remote `plan` or `apply`.

Local `terraform plan` and `terraform apply` still work, but the actual execution happens remotely in Terraform Cloud because of the `cloud` block in `providers.tf`.

## fun_sejmapi Function App resources

`infra/main.tf` now provisions the Azure resources required by the demo `fun_sejmapi` Durable Functions module:

- Linux Consumption Function service plan (`azurerm_service_plan`, SKU `Y1`)
- Dedicated storage account for Function host and Durable state (`azurerm_storage_account`)
- Linux Function App on Java 21 (`azurerm_linux_function_app`)
- System-assigned managed identity on the Function App
- Storage data-plane role assignments for that identity:
   - `Storage Blob Data Contributor`
   - `Storage Queue Data Contributor`
   - `Storage Table Data Contributor`
- Diagnostic settings routing Function logs and metrics to Log Analytics

The Function App runtime settings include durable host storage via managed identity (`AzureWebJobsStorage__*`) and a configurable task hub name (`function_durable_hub_name`).

## Relevant non-secret outputs

Use these outputs to discover the deployed Function infrastructure without exposing secrets:

- `function_app_name`
- `function_app_default_hostname`
- `function_app_id`
- `function_service_plan_name`
- `function_service_plan_id`
- `function_storage_account_name`
- `function_storage_account_id`
- `function_storage_blob_service_endpoint`
- `function_storage_queue_service_endpoint`
- `function_storage_table_service_endpoint`

Example:

```bash
terraform output -raw function_app_name
terraform output -raw function_app_default_hostname
terraform output -raw function_storage_account_name
```

Do not print or share sensitive outputs in logs or documentation.

## Passing Key Vault endpoint to Spring Boot

Use Terraform output `key_vault_uri` as the single source of truth for the app runtime endpoint.

Example:

```bash
export AZURE_KEYVAULT_ENABLED=true
export AZURE_KEYVAULT_ENDPOINT="$(terraform output -raw key_vault_uri)"
```

Then start the app from the repository root:

```bash
mvn spring-boot:run
```
