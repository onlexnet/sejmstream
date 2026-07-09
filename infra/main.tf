data "azurerm_client_config" "current" {}

resource "random_string" "suffix" {
  length  = 5
  upper   = false
  special = false
}

locals {
  name_prefix                        = "sejmstr"
  environment                        = "dev"
  location                           = "westeurope"
  sejmstream_support_group_object_id = "71bccddd-af6e-4f54-921b-407b6094ed61"
  resource_prefix                    = "${local.name_prefix}-${local.environment}"
  global_suffix                      = random_string.suffix.result

  github_secret_values = {
    AZURE_CLIENT_ID        = data.azurerm_client_config.current.client_id
    AZURE_TENANT_ID        = data.azurerm_client_config.current.tenant_id
    AZURE_SUBSCRIPTION_ID  = data.azurerm_client_config.current.subscription_id
    AZURE_FUNCTIONAPP_NAME = azurerm_function_app_flex_consumption.main.name
  }

  github_environment_secret_entries = flatten([
    for env_name, _ in var.github_environments : [
      for secret_name, secret_value in local.github_secret_values : {
        key             = "${env_name}:${secret_name}"
        environment     = env_name
        secret_name     = secret_name
        plaintext_value = secret_value
      }
    ]
  ])

  common_tags = merge(
    {
      application = "sejmstream"
      environment = local.environment
      managed_by  = "terraform"
    },
    var.tags
  )

  # ACR config kept for potential future re-enable.
  # acr_name       = "${local.name_prefix}${local.environment}${local.global_suffix}"
  key_vault_name = "${local.name_prefix}-${local.environment}-kv-${local.global_suffix}"

  function_service_plan_name    = "${local.resource_prefix}-func-plan-flex"
  function_storage_account_name = "${local.name_prefix}${local.environment}fn${local.global_suffix}"
  function_app_name             = "${local.resource_prefix}-func-flex-${local.global_suffix}"
}

resource "azurerm_resource_group" "main" {
  name     = "${local.resource_prefix}-rg"
  location = local.location
  tags     = local.common_tags
}

# resource "azurerm_role_assignment" "sejmstream_support_contributor" {
#   scope                = azurerm_resource_group.main.id
#   role_definition_name = "Contributor"
#   principal_id         = local.sejmstream_support_group_object_id
# }

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${local.resource_prefix}-law"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_analytics_retention_in_days
  tags                = local.common_tags
}

resource "azurerm_container_app_environment" "main" {
  name                       = "${local.resource_prefix}-cae"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  logs_destination           = "log-analytics"
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = local.common_tags
}

# resource "azurerm_container_registry" "main" {
#   name                = local.acr_name
#   resource_group_name = azurerm_resource_group.main.name
#   location            = azurerm_resource_group.main.location
#   sku                 = var.container_registry_sku
#   admin_enabled       = false
#   tags                = local.common_tags
# }

resource "azurerm_application_insights" "main" {
  count               = 1
  name                = "${local.resource_prefix}-appi"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  application_type    = "web"
  workspace_id        = azurerm_log_analytics_workspace.main.id
  tags                = local.common_tags
}

resource "azurerm_service_plan" "function_app" {
  name                = local.function_service_plan_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  os_type             = "Linux"
  sku_name            = "FC1"
  tags                = local.common_tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "azurerm_storage_account" "function_app" {
  name                     = local.function_storage_account_name
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"
  tags                     = local.common_tags
}

resource "azurerm_storage_container" "function_app_deployment" {
  name                  = "function-deploy"
  storage_account_id    = azurerm_storage_account.function_app.id
  container_access_type = "private"
}

resource "azurerm_storage_queue" "interpellation_publish" {
  name               = var.interpellation_publish_queue_name
  storage_account_id = azurerm_storage_account.function_app.id
}

resource "azurerm_storage_queue" "interpellation_publish_dead_letter" {
  name               = var.interpellation_publish_dead_letter_queue_name
  storage_account_id = azurerm_storage_account.function_app.id
}

resource "azurerm_function_app_flex_consumption" "main" {
  name                = local.function_app_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  service_plan_id = azurerm_service_plan.function_app.id

  storage_container_type     = "blobContainer"
  storage_container_endpoint = "${azurerm_storage_account.function_app.primary_blob_endpoint}${azurerm_storage_container.function_app_deployment.name}"
  # TODO: Switch back to SystemAssignedIdentity once Flex Consumption + Managed Identity
  # deployment is fully supported by Azure. As of mid-2026 the Kudu SCM host on Flex
  # Consumption cannot obtain an MSI token (IMDS returns 400), causing all Kudu-based
  # deployments to fail with MSITokenUnavailableException. Tracking issue:
  # https://github.com/hashicorp/terraform-provider-azurerm/issues/29993
  storage_authentication_type = "StorageAccountConnectionString"
  storage_access_key          = azurerm_storage_account.function_app.primary_access_key

  runtime_name           = "java"
  runtime_version        = "25"
  maximum_instance_count = 100
  instance_memory_in_mb  = 2048

  https_only = true

  identity {
    type = "SystemAssigned"
  }

  site_config {}

  app_settings = merge(
    {
      AzureFunctionsJobHost__extensions__durableTask__hubName = var.function_durable_hub_name
      # Required for Flex Consumption when using connection-string storage auth.
      # Remove together with storage_access_key above once MSI is fully supported.
      AzureWebJobsStorage                            = azurerm_storage_account.function_app.primary_connection_string
      APPLICATIONINSIGHTS_CONNECTION_STRING          = azurerm_application_insights.main[0].connection_string
      APPINSIGHTS_INSTRUMENTATIONKEY                 = azurerm_application_insights.main[0].instrumentation_key
      FB_TOKEN                                       = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.facebook_token[0].versionless_id})"
      DB_URL                                         = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.spring_datasource_url[0].versionless_id})"
      DB_USERNAME                                    = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.spring_datasource_username[0].versionless_id})"
      DB_PASSWORD                                    = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.spring_datasource_password[0].versionless_id})"
      TELEGRAM_BOT_TOKEN                             = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.telegram_bot_token[0].versionless_id})"
      TELEGRAM_ALLOWED_CHAT_ID                       = "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault_secret.telegram_allowed_chat_id[0].versionless_id})"
      INTERPELLATION_PUBLISH_QUEUE_NAME              = azurerm_storage_queue.interpellation_publish.name
      INTERPELLATION_PUBLISH_DEAD_LETTER_QUEUE_NAME  = azurerm_storage_queue.interpellation_publish_dead_letter.name
      INTERPELLATION_PUBLISH_MAX_ATTEMPTS            = tostring(var.interpellation_publish_max_attempts)
      INTERPELLATION_PUBLISH_RETRY_DELAY_SECONDS     = tostring(var.interpellation_publish_retry_delay_seconds)
      INTERPELLATION_PUBLISH_BACKOFF_MULTIPLIER      = tostring(var.interpellation_publish_backoff_multiplier)
      INTERPELLATION_PUBLISH_MAX_RETRY_DELAY_SECONDS = tostring(var.interpellation_publish_max_retry_delay_seconds)
    }
  )

  tags = local.common_tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "azurerm_role_assignment" "function_storage_blob_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_function_app_flex_consumption.main.identity[0].principal_id
}

resource "azurerm_role_assignment" "deployment_storage_blob_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_role_assignment" "function_storage_queue_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Queue Data Contributor"
  principal_id         = azurerm_function_app_flex_consumption.main.identity[0].principal_id
}

resource "azurerm_role_assignment" "function_storage_table_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Table Data Contributor"
  principal_id         = azurerm_function_app_flex_consumption.main.identity[0].principal_id
}

resource "azurerm_monitor_diagnostic_setting" "function_app" {
  name                       = "function-app-diagnostics"
  target_resource_id         = azurerm_function_app_flex_consumption.main.id
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id

  enabled_log {
    category = "FunctionAppLogs"
  }

  enabled_metric {
    category = "AllMetrics"
  }
}

resource "azurerm_key_vault" "main" {
  name                       = local.key_vault_name
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  purge_protection_enabled   = true
  tags                       = local.common_tags

  # NOTE: Do NOT add inline access_policy blocks here.
  # The azurerm provider does not support mixing inline access_policy blocks
  # with standalone azurerm_key_vault_access_policy resources — vault updates
  # silently drop the standalone policies, causing Key Vault reference
  # resolution failures ("X Key vault" in the portal).
  # All policies are managed via azurerm_key_vault_access_policy below.
}

# Terraform deployer — full secret management rights.
resource "azurerm_key_vault_access_policy" "deployer" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = data.azurerm_client_config.current.object_id

  secret_permissions = [
    "Delete",
    "Get",
    "List",
    "Purge",
    "Recover",
    "Set",
  ]
}

# Support group — read-only secret access.
resource "azurerm_key_vault_access_policy" "support_group" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = local.sejmstream_support_group_object_id

  secret_permissions = [
    "Get",
    "List",
  ]
}

import {
  to = azurerm_key_vault_access_policy.support_group
  id = "/subscriptions/97fd5a20-4541-4f0b-8103-215e5f52833e/resourceGroups/sejmstr-dev-rg/providers/Microsoft.KeyVault/vaults/sejmstr-dev-kv-igaug/objectId/71bccddd-af6e-4f54-921b-407b6094ed61"
}

# Function App managed identity — read secrets for Key Vault references.
# Must be a standalone resource (not inline) to avoid a dependency cycle:
# key_vault → function_app → key_vault_secret → key_vault.
resource "azurerm_key_vault_access_policy" "function_app" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = azurerm_function_app_flex_consumption.main.identity[0].principal_id

  secret_permissions = [
    "Get",
  ]
}

resource "azurerm_key_vault_secret" "spring_datasource_url" {
  count        = var.spring_datasource_url == null ? 0 : 1
  name         = "spring-datasource-url"
  value        = var.spring_datasource_url
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "spring_datasource_username" {
  count        = var.spring_datasource_username == null ? 0 : 1
  name         = "spring-datasource-username"
  value        = var.spring_datasource_username
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "spring_datasource_password" {
  count        = var.spring_datasource_password == null ? 0 : 1
  name         = "spring-datasource-password"
  value        = var.spring_datasource_password
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "facebook_token" {
  count        = 1
  name         = "fb-token"
  value        = var.facebook_token
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "telegram_bot_token" {
  count        = 1
  name         = "telegram-bot-token"
  value        = var.telegram_bot_token
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "telegram_allowed_chat_id" {
  count        = 1
  name         = "telegram-allowed-chat-id"
  value        = var.telegram_allowed_chat_id
  key_vault_id = azurerm_key_vault.main.id
}

resource "github_repository_environment" "deployment" {
  for_each = var.github_environments

  repository          = var.github_repo
  environment         = each.key
  wait_timer          = each.value.wait_timer
  prevent_self_review = each.value.prevent_self_review
}

resource "github_actions_environment_secret" "azure" {
  for_each = {
    for entry in local.github_environment_secret_entries : entry.key => entry
  }

  repository  = var.github_repo
  environment = each.value.environment
  secret_name = each.value.secret_name
  value       = each.value.plaintext_value

  depends_on = [github_repository_environment.deployment]
}
