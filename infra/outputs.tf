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

# output "container_registry_id" {
#   value = azurerm_container_registry.main.id
# }
#
# output "container_registry_login_server" {
#   value = azurerm_container_registry.main.login_server
# }

output "function_app_id" {
  value = azurerm_function_app_flex_consumption.main.id
}

output "function_app_name" {
  value = azurerm_function_app_flex_consumption.main.name
}

output "function_app_default_hostname" {
  value = azurerm_function_app_flex_consumption.main.default_hostname
}

output "function_app_principal_id" {
  value = azurerm_function_app_flex_consumption.main.identity[0].principal_id
}

output "application_insights_connection_string" {
  value     = try(azurerm_application_insights.main[0].connection_string, null)
  sensitive = true
}

output "function_service_plan_id" {
  value = azurerm_service_plan.function_app.id
}

output "function_service_plan_name" {
  value = azurerm_service_plan.function_app.name
}

output "function_storage_account_id" {
  value = azurerm_storage_account.function_app.id
}

output "function_storage_account_name" {
  value = azurerm_storage_account.function_app.name
}

output "function_storage_blob_service_endpoint" {
  value = azurerm_storage_account.function_app.primary_blob_endpoint
}

output "function_storage_queue_service_endpoint" {
  value = azurerm_storage_account.function_app.primary_queue_endpoint
}

output "interpellation_publish_queue_name" {
  value = azurerm_storage_queue.interpellation_publish.name
}

output "interpellation_publish_queue_url" {
  value = "${azurerm_storage_account.function_app.primary_queue_endpoint}${azurerm_storage_queue.interpellation_publish.name}"
}

output "interpellation_publish_dead_letter_queue_name" {
  value = azurerm_storage_queue.interpellation_publish_dead_letter.name
}

output "interpellation_publish_dead_letter_queue_url" {
  value = "${azurerm_storage_account.function_app.primary_queue_endpoint}${azurerm_storage_queue.interpellation_publish_dead_letter.name}"
}

output "function_storage_table_service_endpoint" {
  value = azurerm_storage_account.function_app.primary_table_endpoint
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

output "spring_datasource_url_secret_versionless_id" {
  value = try(azurerm_key_vault_secret.spring_datasource_url[0].versionless_id, null)
}

output "spring_datasource_username_secret_versionless_id" {
  value = try(azurerm_key_vault_secret.spring_datasource_username[0].versionless_id, null)
}

output "spring_datasource_password_secret_versionless_id" {
  value     = try(azurerm_key_vault_secret.spring_datasource_password[0].versionless_id, null)
  sensitive = true
}

output "facebook_token_secret_versionless_id" {
  value     = try(azurerm_key_vault_secret.facebook_token[0].versionless_id, null)
  sensitive = true
}

