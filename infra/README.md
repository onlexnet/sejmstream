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
