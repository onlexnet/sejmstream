variable "foundry_agent_location" {
  description = "Region supporting Foundry Agent Service and the selected model deployment."
  type        = string
  default     = "swedencentral"
}

variable "foundry_agent_model" {
  description = "Model deployment for the example prompt agent. Check regional availability and quota before applying."
  type = object({
    name     = string
    version  = string
    sku      = string
    capacity = number
  })
  default = {
    name     = "gpt-4.1-mini"
    version  = "2025-04-14"
    sku      = "GlobalStandard"
    capacity = 10
  }

  validation {
    condition     = var.foundry_agent_model.capacity >= 1 && floor(var.foundry_agent_model.capacity) == var.foundry_agent_model.capacity
    error_message = "foundry_agent_model.capacity must be a positive integer."
  }
}

resource "azapi_resource" "foundry_agent_account" {
  type      = "Microsoft.CognitiveServices/accounts@2025-06-01"
  name      = "${local.resource_prefix}-agents-${local.global_suffix}"
  parent_id = azurerm_resource_group.main.id
  location  = var.foundry_agent_location
  tags      = local.common_tags

  identity {
    type = "SystemAssigned"
  }

  body = {
    kind = "AIServices"
    sku = {
      name = "S0"
    }
    properties = {
      allowProjectManagement = true
      customSubDomainName    = "${local.resource_prefix}-agents-${local.global_suffix}"
      disableLocalAuth       = true
      publicNetworkAccess    = "Enabled"
    }
  }
}

resource "azapi_resource" "foundry_agent_project" {
  type      = "Microsoft.CognitiveServices/accounts/projects@2025-06-01"
  name      = "sejmstream-agents"
  parent_id = azapi_resource.foundry_agent_account.id
  location  = var.foundry_agent_location
  tags      = local.common_tags

  identity {
    type = "SystemAssigned"
  }

  body = {
    properties = {
      displayName = "SejmStream agents"
      description = "Versioned prompt agents maintained in the SejmStream repository."
    }
  }
}

resource "azurerm_cognitive_deployment" "foundry_agent" {
  name                 = "sejm-analyst-model"
  cognitive_account_id = azapi_resource.foundry_agent_account.id

  model {
    format  = "OpenAI"
    name    = var.foundry_agent_model.name
    version = var.foundry_agent_model.version
  }

  sku {
    name     = var.foundry_agent_model.sku
    capacity = var.foundry_agent_model.capacity
  }
}

resource "azurerm_role_assignment" "foundry_agent_deployer" {
  scope              = azapi_resource.foundry_agent_account.id
  role_definition_id = local.foundry_user_role_definition_id
  principal_id       = data.azurerm_client_config.current.object_id
}

resource "azurerm_role_assignment" "foundry_agent_project" {
  scope              = azapi_resource.foundry_agent_account.id
  role_definition_id = local.foundry_user_role_definition_id
  principal_id       = azapi_resource.foundry_agent_project.identity[0].principal_id
}

moved {
  from = azapi_resource.foundry_agent_account[0]
  to   = azapi_resource.foundry_agent_account
}

moved {
  from = azapi_resource.foundry_agent_project[0]
  to   = azapi_resource.foundry_agent_project
}

moved {
  from = azurerm_cognitive_deployment.foundry_agent[0]
  to   = azurerm_cognitive_deployment.foundry_agent
}

moved {
  from = azurerm_role_assignment.foundry_agent_deployer[0]
  to   = azurerm_role_assignment.foundry_agent_deployer
}

moved {
  from = azurerm_role_assignment.foundry_agent_project[0]
  to   = azurerm_role_assignment.foundry_agent_project
}

locals {
  foundry_user_role_definition_id = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/providers/Microsoft.Authorization/roleDefinitions/53ca6127-db72-4b80-b1b0-d745d6d5456d"
  foundry_agent_project_endpoint  = "https://${azapi_resource.foundry_agent_account.name}.services.ai.azure.com/api/projects/${azapi_resource.foundry_agent_project.name}"
  foundry_agent_github_variables = {
    AZURE_AI_PROJECT_ENDPOINT      = local.foundry_agent_project_endpoint
    AZURE_AI_MODEL_DEPLOYMENT_NAME = azurerm_cognitive_deployment.foundry_agent.name
  }
  foundry_agent_environment_variables = {
    for entry in flatten([
      for environment, _ in var.github_environments : [
        for name, value in local.foundry_agent_github_variables : {
          key         = "${environment}:${name}"
          environment = environment
          name        = name
          value       = value
        }
      ]
    ]) : entry.key => entry
  }
}

resource "github_actions_environment_variable" "foundry_agent" {
  for_each = local.foundry_agent_environment_variables

  repository    = var.github_repo
  environment   = github_repository_environment.deployment[each.value.environment].environment
  variable_name = each.value.name
  value         = each.value.value
}

output "foundry_agent_project_endpoint" {
  description = "Project endpoint used by the agent deployment script."
  value       = local.foundry_agent_project_endpoint
}

output "foundry_agent_model_deployment_name" {
  value = azurerm_cognitive_deployment.foundry_agent.name
}
