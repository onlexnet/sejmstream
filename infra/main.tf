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

