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

