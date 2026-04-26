output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "location" {
  value = azurerm_resource_group.main.location
}

output "container_app_environment_id" {
  value = azurerm_container_app_environment.main.id
}

output "container_app_environment_default_domain" {
  value = azurerm_container_app_environment.main.default_domain
}

output "container_registry_id" {
  value = azurerm_container_registry.main.id
}

output "container_registry_login_server" {
  value = azurerm_container_registry.main.login_server
}

output "key_vault_id" {
  value = azurerm_key_vault.main.id
}

output "key_vault_name" {
  value = azurerm_key_vault.main.name
}

output "key_vault_uri" {
  value = azurerm_key_vault.main.vault_uri
}

output "postgres_server_id" {
  value = azurerm_postgresql_flexible_server.main.id
}

output "postgres_server_fqdn" {
  value = azurerm_postgresql_flexible_server.main.fqdn
}

output "postgres_database_name" {
  value = azurerm_postgresql_flexible_server_database.app.name
}

output "spring_datasource_url_secret_versionless_id" {
  value = azurerm_key_vault_secret.spring_datasource_url.versionless_id
}

output "spring_datasource_username_secret_versionless_id" {
  value = azurerm_key_vault_secret.spring_datasource_username.versionless_id
}

output "spring_datasource_password_secret_versionless_id" {
  value     = azurerm_key_vault_secret.spring_datasource_password.versionless_id
  sensitive = true
}

output "facebook_token_secret_versionless_id" {
  value     = try(azurerm_key_vault_secret.facebook_token[0].versionless_id, null)
  sensitive = true
}

