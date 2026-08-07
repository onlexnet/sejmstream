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

variable "function_durable_scheduler_sku_name" {
  description = "SKU name for the Durable Task Scheduler backend (Consumption only)."
  type        = string
  default     = "Consumption"

  validation {
    condition     = var.function_durable_scheduler_sku_name == "Consumption"
    error_message = "function_durable_scheduler_sku_name must be Consumption."
  }
}

variable "function_durable_scheduler_sku_capacity" {
  description = "Capacity for the Durable Task Scheduler SKU (ignored for Consumption)."
  type        = number
  default     = 1

  validation {
    condition     = var.function_durable_scheduler_sku_capacity >= 1
    error_message = "function_durable_scheduler_sku_capacity must be at least 1."
  }
}

variable "function_durable_scheduler_public_network_access" {
  description = "Public network access mode for Durable Task Scheduler."
  type        = string
  default     = "Disabled"

  validation {
    condition     = contains(["Enabled", "Disabled"], var.function_durable_scheduler_public_network_access)
    error_message = "function_durable_scheduler_public_network_access must be Enabled or Disabled."
  }
}

variable "function_durable_scheduler_ip_allowlist" {
  description = "IP allowlist entries for Durable Task Scheduler public endpoint (IPv4/IPv6/CIDR)."
  type        = list(string)
  default     = ["127.0.0.1/32"]

  validation {
    condition     = length(var.function_durable_scheduler_ip_allowlist) > 0
    error_message = "function_durable_scheduler_ip_allowlist must contain at least one IP/CIDR entry."
  }
}

variable "interpellation_publish_queue_name" {
  description = "Main Azure Storage Queue name for interpellation publish jobs."
  type        = string
  default     = "sejm-interpellations-publish"

  validation {
    condition     = can(regex("^[a-z0-9](?:[a-z0-9-]{1,61})[a-z0-9]$", var.interpellation_publish_queue_name))
    error_message = "interpellation_publish_queue_name must be 3-63 chars, lowercase alphanumeric or hyphen, and start/end with alphanumeric."
  }
}

variable "interpellation_publish_dead_letter_queue_name" {
  description = "Dead-letter Azure Storage Queue name for interpellation publish jobs."
  type        = string
  default     = "sejm-interpellations-publish-deadletter"

  validation {
    condition     = can(regex("^[a-z0-9](?:[a-z0-9-]{1,61})[a-z0-9]$", var.interpellation_publish_dead_letter_queue_name))
    error_message = "interpellation_publish_dead_letter_queue_name must be 3-63 chars, lowercase alphanumeric or hyphen, and start/end with alphanumeric."
  }
}

variable "interpellation_publish_max_attempts" {
  description = "Maximum publish attempts before moving a message to dead-letter handling."
  type        = number
  default     = 5

  validation {
    condition     = var.interpellation_publish_max_attempts >= 1 && var.interpellation_publish_max_attempts <= 20
    error_message = "interpellation_publish_max_attempts must be between 1 and 20."
  }
}

variable "interpellation_publish_retry_delay_seconds" {
  description = "Base retry delay in seconds for interpellation publish retries."
  type        = number
  default     = 60

  validation {
    condition     = var.interpellation_publish_retry_delay_seconds >= 1 && var.interpellation_publish_retry_delay_seconds <= 86400
    error_message = "interpellation_publish_retry_delay_seconds must be between 1 and 86400."
  }
}

variable "interpellation_publish_backoff_multiplier" {
  description = "Exponential backoff multiplier for interpellation publish retries."
  type        = number
  default     = 2.0

  validation {
    condition     = var.interpellation_publish_backoff_multiplier >= 1.0 && var.interpellation_publish_backoff_multiplier <= 10.0
    error_message = "interpellation_publish_backoff_multiplier must be between 1.0 and 10.0."
  }
}

variable "interpellation_publish_max_retry_delay_seconds" {
  description = "Maximum retry delay cap in seconds for interpellation publish retries."
  type        = number
  default     = 900

  validation {
    condition     = var.interpellation_publish_max_retry_delay_seconds >= 1 && var.interpellation_publish_max_retry_delay_seconds <= 604800
    error_message = "interpellation_publish_max_retry_delay_seconds must be between 1 and 604800."
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

