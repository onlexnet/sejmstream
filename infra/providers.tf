terraform {
  required_version = ">= 1.8.0"

  cloud {
    organization = "onlexnet"

    workspaces {
      name = "sejmstream-dev"
    }
  }

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.75.0" # it means: version: 4.50.*
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "azurerm" {

  features {}

  # Avoid RP auto-registration when the identity lacks subscription-level permissions.
  resource_provider_registrations = "none"

  use_cli  = false
  use_oidc = true
}
