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
      version = "~> 5.3"
    }
    azapi = {
      source  = "azure/azapi"
      version = "~> 2.3"
    }
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "github" {
  owner = var.github_owner

  app_auth {
    id              = var.github_app_id
    installation_id = var.github_app_installation_id
    pem_file        = var.github_app_pem
  }
}

provider "azurerm" {

  features {}

  # Avoid RP auto-registration when the identity lacks subscription-level permissions.
  resource_provider_registrations = "none"

  use_cli  = false
  use_oidc = true
}

provider "azapi" {
  use_cli  = false
  use_oidc = true
}
