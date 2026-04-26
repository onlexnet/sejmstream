data "azurerm_client_config" "current" {}

resource "random_string" "suffix" {
  length  = 5
  upper   = false
  special = false
}

resource "random_password" "postgres_admin" {
  length           = 24
  special          = true
  override_special = "_%@"
  min_lower        = 1
  min_upper        = 1
  min_numeric      = 1
  min_special      = 1
}

locals {
  resource_prefix = "${var.name_prefix}-${var.environment}"
  global_suffix   = random_string.suffix.result

  common_tags = merge(
    {
      application = "sejmstream"
      environment = var.environment
      managed_by  = "terraform"
    },
    var.tags
  )

  acr_name               = "${var.name_prefix}${var.environment}${local.global_suffix}"
  key_vault_name         = "${var.name_prefix}-${var.environment}-kv-${local.global_suffix}"
  postgres_server_name   = "${var.name_prefix}-${var.environment}-psql-${local.global_suffix}"
  jdbc_connection_string = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${azurerm_postgresql_flexible_server_database.app.name}?sslmode=require"
}

resource "azurerm_resource_group" "main" {
  name     = "${local.resource_prefix}-rg"
  location = var.location
  tags     = local.common_tags
}

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
}

resource "azurerm_postgresql_flexible_server" "main" {
  name                   = local.postgres_server_name
  resource_group_name    = azurerm_resource_group.main.name
  location               = azurerm_resource_group.main.location
  version                = var.postgres_version
  administrator_login    = var.postgres_admin_username
  administrator_password = random_password.postgres_admin.result

  # Azure PostgreSQL Flexible Server SKU must include a tier prefix (B_, GP_, MO_).
  sku_name                      = var.postgres_sku_name
  storage_mb                    = var.postgres_storage_mb
  backup_retention_days         = var.postgres_backup_retention_days
  auto_grow_enabled             = true
  public_network_access_enabled = false
  tags                          = local.common_tags
}

resource "azurerm_postgresql_flexible_server_database" "app" {
  name      = var.postgres_database_name
  server_id = azurerm_postgresql_flexible_server.main.id
  collation = "en_US.utf8"
  charset   = "UTF8"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "developer_ip" {
  count            = var.developer_public_ip == null ? 0 : 1
  name             = "allow-developer-ip"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = var.developer_public_ip
  end_ip_address   = var.developer_public_ip
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "office" {
  name             = "allow-office-ip"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "188.241.29.152"
  end_ip_address   = "188.241.29.152"
}

resource "azurerm_advanced_threat_protection" "postgres" {
  target_resource_id = azurerm_postgresql_flexible_server.main.id
  enabled            = true
}

resource "azurerm_key_vault_secret" "spring_datasource_url" {
  name         = "spring-datasource-url"
  value        = local.jdbc_connection_string
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "spring_datasource_username" {
  name         = "spring-datasource-username"
  value        = var.postgres_admin_username
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "spring_datasource_password" {
  name         = "spring-datasource-password"
  value        = random_password.postgres_admin.result
  key_vault_id = azurerm_key_vault.main.id
}

resource "azurerm_key_vault_secret" "facebook_token" {
  count        = var.facebook_token == null ? 0 : 1
  name         = "fb-token"
  value        = var.facebook_token
  key_vault_id = azurerm_key_vault.main.id
}

