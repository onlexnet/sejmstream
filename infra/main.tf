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
    AZURE_FUNCTIONAPP_NAME = azurerm_linux_function_app.main.name
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

  acr_name       = "${local.name_prefix}${local.environment}${local.global_suffix}"
  key_vault_name = "${local.name_prefix}-${local.environment}-kv-${local.global_suffix}"

  function_service_plan_name    = "${local.resource_prefix}-func-plan"
  function_storage_account_name = "${local.name_prefix}${local.environment}fn${local.global_suffix}"
  function_app_name             = "${local.resource_prefix}-func-${local.global_suffix}"
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

resource "azurerm_container_registry" "main" {
  name                = local.acr_name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.container_registry_sku
  admin_enabled       = false
  tags                = local.common_tags
}

resource "azurerm_application_insights" "main" {
  count               = var.enable_application_insights ? 1 : 0
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
  sku_name            = "Y1"
  tags                = local.common_tags
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

resource "azurerm_linux_function_app" "main" {
  name                = local.function_app_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  service_plan_id               = azurerm_service_plan.function_app.id
  storage_account_name          = azurerm_storage_account.function_app.name
  storage_uses_managed_identity = true

  https_only                  = true
  functions_extension_version = "~4"

  identity {
    type = "SystemAssigned"
  }

  site_config {
    always_on = false

    application_stack {
      java_version = "21"
    }
  }

  # TASK-002 runtime key parity: FUNCTIONS_WORKER_RUNTIME + AzureWebJobsStorage.
  # TASK-003 behavior parity: durable hub name is configurable via Terraform input.
  app_settings = merge(
    {
      FUNCTIONS_WORKER_RUNTIME                                = "java"
      AzureWebJobsStorage__accountName                        = azurerm_storage_account.function_app.name
      AzureWebJobsStorage__blobServiceUri                     = azurerm_storage_account.function_app.primary_blob_endpoint
      AzureWebJobsStorage__queueServiceUri                    = azurerm_storage_account.function_app.primary_queue_endpoint
      AzureWebJobsStorage__tableServiceUri                    = azurerm_storage_account.function_app.primary_table_endpoint
      AzureWebJobsStorage__credential                         = "managedidentity"
      AzureFunctionsJobHost__extensions__durableTask__hubName = var.function_durable_hub_name
    },
    var.enable_application_insights ? {
      APPLICATIONINSIGHTS_CONNECTION_STRING = azurerm_application_insights.main[0].connection_string
    } : {}
  )

  tags = local.common_tags
}

resource "azurerm_role_assignment" "function_storage_blob_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_linux_function_app.main.identity[0].principal_id
}

resource "azurerm_role_assignment" "function_storage_queue_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Queue Data Contributor"
  principal_id         = azurerm_linux_function_app.main.identity[0].principal_id
}

resource "azurerm_role_assignment" "function_storage_table_data_contributor" {
  scope                = azurerm_storage_account.function_app.id
  role_definition_name = "Storage Table Data Contributor"
  principal_id         = azurerm_linux_function_app.main.identity[0].principal_id
}

resource "azurerm_role_assignment" "function_key_vault_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_linux_function_app.main.identity[0].principal_id
}

resource "azurerm_monitor_diagnostic_setting" "function_app" {
  name                       = "function-app-diagnostics"
  target_resource_id         = azurerm_linux_function_app.main.id
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

  access_policy {
    tenant_id = data.azurerm_client_config.current.tenant_id
    object_id = data.azurerm_client_config.current.object_id

    secret_permissions = [
      "Delete",
      "Get",
      "List",
      "Purge",
      "Recover",
      "Set",
    ]
  }

  access_policy {
    tenant_id = data.azurerm_client_config.current.tenant_id
    object_id = local.sejmstream_support_group_object_id

    secret_permissions = [
      "Get",
      "List",
    ]
  }
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
  count        = var.facebook_token == null ? 0 : 1
  name         = "fb-token"
  value        = var.facebook_token
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

  repository      = var.github_repo
  environment     = each.value.environment
  secret_name     = each.value.secret_name
  plaintext_value = each.value.plaintext_value

  depends_on = [github_repository_environment.deployment]
}

