variable "name_prefix" {
  description = "Short lowercase prefix used to build resource names."
  type        = string
  default     = "sejmstr"

  validation {
    condition     = can(regex("^[a-z0-9]{3,10}$", var.name_prefix))
    error_message = "name_prefix must contain 3-10 lowercase letters or digits."
  }
}

variable "environment" {
  description = "Short environment name appended to resource names."
  type        = string
  default     = "dev"

  validation {
    condition     = can(regex("^[a-z0-9]{2,4}$", var.environment))
    error_message = "environment must contain 2-4 lowercase letters or digits."
  }
}

variable "location" {
  description = "Azure region for all resources."
  type        = string
  default     = "westeurope"
}

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

variable "postgres_database_name" {
  description = "Application database name."
  type        = string
  default     = "sejmstream"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.postgres_database_name))
    error_message = "postgres_database_name must start with a letter and contain only letters, digits or underscores."
  }
}

variable "postgres_admin_username" {
  description = "Administrator username for PostgreSQL Flexible Server."
  type        = string
  default     = "sejmadmin"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{2,15}$", var.postgres_admin_username))
    error_message = "postgres_admin_username must contain 3-16 lowercase letters or digits and start with a letter."
  }
}

variable "postgres_version" {
  description = "PostgreSQL major version."
  type        = string
  default     = "16"

  validation {
    condition     = contains(["11", "12", "13", "14", "15", "16", "17", "18"], var.postgres_version)
    error_message = "postgres_version must be one of 11, 12, 13, 14, 15, 16, 17 or 18."
  }
}

variable "postgres_sku_name" {
  description = "SKU for PostgreSQL Flexible Server. Burstable SKUs use the B_ prefix, e.g. B_Standard_B1ms."
  type        = string
  # Examples: B_Standard_B1ms, GP_Standard_D2s_v3, MO_Standard_E2s_v3.
  default     = "B_Standard_B1ms"

  validation {
    condition     = can(regex("^(B|GP|MO)_", var.postgres_sku_name))
    error_message = "postgres_sku_name must start with B_, GP_, or MO_ (for example B_Standard_B1ms)."
  }
}

variable "postgres_storage_mb" {
  description = "Storage size for PostgreSQL Flexible Server. Minimum for cost optimization in dev."
  type        = number
  default     = 32768

  validation {
    condition = contains(
      [32768, 65536, 131072, 262144, 524288, 1048576, 2097152, 4193280, 4194304, 8388608, 16777216, 33553408],
      var.postgres_storage_mb
    )
    error_message = "postgres_storage_mb must be one of the Azure supported sizes for Flexible Server."
  }
}

variable "postgres_backup_retention_days" {
  description = "Backup retention period for PostgreSQL Flexible Server."
  type        = number
  default     = 7

  validation {
    condition     = var.postgres_backup_retention_days >= 7 && var.postgres_backup_retention_days <= 35
    error_message = "postgres_backup_retention_days must be between 7 and 35."
  }
}

variable "developer_public_ip" {
  description = "Optional public IPv4 address that should also be allowed to connect to PostgreSQL."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.developer_public_ip == null || can(regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}$", var.developer_public_ip))
    error_message = "developer_public_ip must be a valid IPv4 address or null."
  }
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

