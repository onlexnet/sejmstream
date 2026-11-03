mock_provider "azurerm" {
  mock_data "azurerm_client_config" {
    defaults = {
      client_id       = "00000000-0000-0000-0000-000000000001"
      object_id       = "00000000-0000-0000-0000-000000000002"
      tenant_id       = "00000000-0000-0000-0000-000000000003"
      subscription_id = "00000000-0000-0000-0000-000000000004"
    }
  }
}

mock_provider "azapi" {}
mock_provider "github" {}
mock_provider "random" {
  override_during = plan

  mock_resource "random_string" {
    defaults = {
      result = "abcde"
    }
  }
}

variables {
  telegram_bot_token         = "test-token"
  telegram_allowed_chat_id   = "123"
  facebook_token             = "test-token"
  spring_datasource_url      = "jdbc:postgresql://localhost/test"
  spring_datasource_username = "test"
  spring_datasource_password = "test-password"
  github_app_id              = "1"
  github_app_installation_id = "1"
  github_app_pem             = "test-pem"
}

run "creates_foundry_by_default" {
  command = plan

  # Exclude the shared root's live Key Vault import from these mocked feature plans.
  plan_options {
    target = [
      azapi_resource.foundry_agent_account,
      azapi_resource.foundry_agent_project,
      azurerm_cognitive_deployment.foundry_agent,
      azurerm_role_assignment.foundry_agent_deployer,
      azurerm_role_assignment.foundry_agent_project,
      github_actions_environment_variable.foundry_agent,
    ]
  }

  assert {
    condition = (
      azapi_resource.foundry_agent_account.body.properties.allowProjectManagement &&
      azapi_resource.foundry_agent_account.body.properties.disableLocalAuth &&
      azapi_resource.foundry_agent_project.location == azapi_resource.foundry_agent_account.location &&
      azurerm_role_assignment.foundry_agent_deployer.principal_id == data.azurerm_client_config.current.object_id &&
      length(github_actions_environment_variable.foundry_agent) == 2 &&
      github_actions_environment_variable.foundry_agent["prod:AZURE_AI_MODEL_DEPLOYMENT_NAME"].value == azurerm_cognitive_deployment.foundry_agent.name
    )
    error_message = "Foundry must provision the project, model, RBAC and deployment variables without an opt-in."
  }

  assert {
    condition = (
      output.foundry_agent_project_endpoint == "https://sejmstr-dev-agents-abcde.services.ai.azure.com/api/projects/sejmstream-agents" &&
      github_actions_environment_variable.foundry_agent["prod:AZURE_AI_PROJECT_ENDPOINT"].value == output.foundry_agent_project_endpoint
    )
    error_message = "The project endpoint must be exported to Terraform outputs and the deployment environment."
  }

  assert {
    condition = (
      startswith(
        azurerm_role_assignment.foundry_agent_deployer.role_definition_id,
        "/subscriptions/${data.azurerm_client_config.current.subscription_id}/providers/Microsoft.Authorization/roleDefinitions/"
      ) &&
      azurerm_role_assignment.foundry_agent_project.role_definition_id == azurerm_role_assignment.foundry_agent_deployer.role_definition_id
    )
    error_message = "Both assignments must use the same subscription-qualified role ID instead of resolving a display name."
  }
}

run "configures_each_deployment_environment" {
  command = plan

  plan_options {
    target = [
      azapi_resource.foundry_agent_account,
      azapi_resource.foundry_agent_project,
      azurerm_cognitive_deployment.foundry_agent,
      azurerm_role_assignment.foundry_agent_deployer,
      azurerm_role_assignment.foundry_agent_project,
      github_actions_environment_variable.foundry_agent,
    ]
  }

  variables {
    github_environments = {
      prod    = {}
      staging = {}
    }
  }

  assert {
    condition = (
      length(github_actions_environment_variable.foundry_agent) == 4 &&
      github_actions_environment_variable.foundry_agent["staging:AZURE_AI_PROJECT_ENDPOINT"].value == output.foundry_agent_project_endpoint &&
      github_actions_environment_variable.foundry_agent["staging:AZURE_AI_MODEL_DEPLOYMENT_NAME"].value == azurerm_cognitive_deployment.foundry_agent.name
    )
    error_message = "Every deployment environment must receive the Foundry project endpoint and model name."
  }
}
