variable "container_registry_sku" {
  description = "SKU for Azure Container Registry."
  type        = string
  default     = "Basic"

  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.container_registry_sku)
    error_message = "container_registry_sku must be Basic, Standard or Premium."
  }
}

variable "log_analytics_retention_in_days" {
  description = "Log retention for the shared Log Analytics workspace. Minimum 30 days as required by Azure."
  type        = number
  default     = 30
}

variable "function_durable_hub_name" {
  description = "Durable Functions task hub name used by the fun_sejmapi demo slice."
  type        = string
  default     = "SejmApiDemoHub"

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9]{2,44}$", var.function_durable_hub_name))
    error_message = "function_durable_hub_name must start with a letter and contain 3-45 alphanumeric characters."
  }
}

variable "spring_datasource_url" {
  description = "Optional JDBC URL for external PostgreSQL (for example Neon)."
  type        = string
  default     = null
  nullable    = true
}

variable "spring_datasource_username" {
  description = "Optional database username for external PostgreSQL (for example Neon)."
  type        = string
  default     = null
  nullable    = true
}

variable "spring_datasource_password" {
  description = "Optional database password for external PostgreSQL (for example Neon)."
  type        = string
  default     = null
  nullable    = true
  sensitive   = true
}

variable "facebook_token" {
  description = "Optional Facebook token to preload into Key Vault."
  type        = string
  default     = null
  nullable    = true
  sensitive   = true
}

variable "tags" {
  description = "Optional tags applied to all resources."
  type        = map(string)
  default     = {}
}

