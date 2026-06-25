# variable "container_registry_sku" {
#   description = "SKU for Azure Container Registry."
#   type        = string
#   default     = "Basic"
#
#   validation {
#     condition     = contains(["Basic", "Standard", "Premium"], var.container_registry_sku)
#     error_message = "container_registry_sku must be Basic, Standard or Premium."
#   }
# }

variable "log_analytics_retention_in_days" {
  description = "Log retention for the shared Log Analytics workspace. Minimum 30 days as required by Azure."
  type        = number
  default     = 30
}

variable "function_durable_hub_name" {
  description = "Durable Functions task hub name used by the fun_sejmlive demo slice."
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

variable "telegram_bot_token" {
  description = "Telegram bot token to preload into Key Vault (required)."
  type        = string
  sensitive   = true

  validation {
    condition     = trimspace(var.telegram_bot_token) != ""
    error_message = "telegram_bot_token must be a non-empty string."
  }
}

variable "telegram_allowed_chat_id" {
  description = "Telegram chat ID allowed to invoke bot commands (required)."
  type        = string

  validation {
    condition     = can(regex("^-?[0-9]+$", trimspace(var.telegram_allowed_chat_id)))
    error_message = "telegram_allowed_chat_id must be a numeric Telegram chat id (optionally negative)."
  }
}

variable "github_owner" {
  description = "GitHub organization or user owning the repository."
  type        = string
  default     = "onlexnet"
}

variable "github_repo" {
  description = "GitHub repository name that hosts the deployment workflow."
  type        = string
  default     = "sejmstream"
}

variable "github_app_id" {
  description = "GitHub App ID used for provider authentication."
  type        = string
}

variable "github_app_installation_id" {
  description = "GitHub App installation ID used for provider authentication."
  type        = string
}

variable "github_app_pem" {
  description = "GitHub App private key PEM content used for provider authentication."
  type        = string
  sensitive   = true
}

variable "github_environments" {
  description = "GitHub environments that should receive deployment secrets."
  type = map(object({
    wait_timer          = optional(number, 0)
    prevent_self_review = optional(bool, false)
  }))
  default = {
    prod = {}
  }
}

variable "tags" {
  description = "Optional tags applied to all resources."
  type        = map(string)
  default     = {}
}

