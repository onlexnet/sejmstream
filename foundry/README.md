# Example Foundry agent

`sejm-analyst` is a prompt agent that summarizes parliamentary documents supplied
by the caller. It responds in Polish, distinguishes missing information, and has
no browsing, database, Sejm API or publishing tools. It is not connected to the
running Functions application.

The manifest in `agents/sejm-analyst/agent.json` is a small repository-specific
configuration file, not a Foundry or Copilot agent schema. `deploy_agent.py` reads
it and `instructions.md`, builds a `PromptAgentDefinition`, creates an immutable
agent version, and reads that version back from Foundry. No hosted agent,
container registry or `azd` installation is required.

## 1. Provision infrastructure

The existing Terraform root and Terraform Cloud workspace are reused. See
[`infra/README.md`](../infra/README.md) for authentication and GitHub App setup.
Foundry resources are an unconditional part of this infrastructure. Applying
Terraform provisions them alongside the existing application resources and can
incur Azure costs.

Review a Terraform plan and apply it through the existing `infra/` workflow,
using the `onlexnet/sejmstream-dev` Terraform Cloud workspace:

```bash
terraform -chdir=infra init
terraform -chdir=infra plan
terraform -chdir=infra apply
```

These commands use remote execution and the existing workspace credentials.
They plan/apply the **entire** infrastructure root, not just Foundry; review all
changes before approving.

State migration blocks preserve the existing Foundry resources if the previous
count-based configuration was already applied.

`infra/foundry-agent.tf` adds:

- A separate Foundry `AIServices` account with project support, system-assigned
  identity, and key authentication disabled.
- A `sejmstream-agents` project with its own managed identity.
- A model deployment named `sejm-analyst-model`, defaulting to `gpt-4.1-mini`
  version `2025-04-14`, `GlobalStandard`, capacity `10`, in `swedencentral`.
- `Foundry User` at the new account scope for the deployment principal and
  project identity.
- GitHub Environment variables `AZURE_AI_PROJECT_ENDPOINT` and
  `AZURE_AI_MODEL_DEPLOYMENT_NAME` for the existing deployment environments.

The built-in `Foundry User` role is referenced by its stable ID
`53ca6127-db72-4b80-b1b0-d745d6d5456d`, not its former display name
`Azure AI User`. This avoids role lookup failures after display-name changes.

The existing location-extraction account/model and Function App are unchanged.
Check model availability and subscription quota before applying. Override
`foundry_agent_location` or the `foundry_agent_model` object in Terraform Cloud
when necessary. `GlobalStandard` does not guarantee EU-only inference processing.
The public endpoint requires Microsoft Entra authentication; this example does
not provision private networking.

The Terraform principal must be able to create Cognitive Services resources and
role assignments. Register `Microsoft.CognitiveServices` on the subscription if
it is not already registered. The GitHub App needs permission to write Actions
environment variables, in addition to the repository's existing environment and
secret management permissions.

## 2. Deploy through GitHub Actions

After applying Terraform, go to **Actions > Foundry agent > Run workflow** and
select **main**. Only this manual run on `main` deploys; pull requests and pushes
run offline validation and tests without Azure credentials.

The deploy job uses the existing **prod** GitHub Environment and its secrets:
`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_SUBSCRIPTION_ID`. Terraform
already maintains these secrets using its deployment principal. That Entra
application must have a federated credential for:

```text
issuer:   https://token.actions.githubusercontent.com
subject:  repo:onlexnet/sejmstream:environment:prod
audience: api://AzureADTokenExchange
```

This is the same environment trust used by `fun_sejmlive-ci.yml`; the example
does not create or alter the application's federated credentials.

The job logs in with OIDC, creates and reads back a new agent version, then
invokes **that exact version** using a synthetic parliamentary document. A
failed, incomplete or empty response fails the job. The response is printed in
the job log. This is a connectivity smoke test, not a semantic quality evaluation.
Every deployment creates a new version, even if the instructions are unchanged.
If the smoke test fails, the created version remains available for diagnosis.

New RBAC assignments can take a few minutes to propagate; rerun the workflow
after propagation if the first deployment returns an authorization error.
Model calls, including the smoke test, incur usage charges.

Open the deployed `sejmstream-agents` project in the Foundry portal and select
**Agents > sejm-analyst** to inspect or test the agent. Treat Git as the source of
truth: portal edits are not synchronized back to this repository.

## Local validation and deployment

Use Python 3.13:

```bash
python -m venv .venv
. .venv/bin/activate
python -m pip install -r foundry/requirements.txt
PYTHONPATH=foundry python -m unittest discover -s foundry/tests -v
python foundry/deploy_agent.py --validate --model sejm-analyst-model
```

For an actual deployment, sign in with `az login`. The signed-in identity needs
`Foundry User` on the Foundry account (provisioning grants it to the Terraform
deployment identity, not automatically to every developer). Export the endpoint
and deployment name from the Terraform outputs:

```bash
export AZURE_AI_PROJECT_ENDPOINT="$(terraform -chdir=infra output -raw foundry_agent_project_endpoint)"
export AZURE_AI_MODEL_DEPLOYMENT_NAME="$(terraform -chdir=infra output -raw foundry_agent_model_deployment_name)"
python foundry/deploy_agent.py --smoke-test
```

Omit `--smoke-test` to create/read back the version without a model invocation.
For another agent, create another manifest/instructions directory and pass
`--manifest foundry/agents/<name>/agent.json` to the same script.

## References

- [Foundry prompt agents](https://learn.microsoft.com/azure/foundry/agents/concepts/prompt-agents)
- [Create a prompt agent](https://learn.microsoft.com/azure/foundry/agents/quickstarts/prompt-agent)
- [Foundry project ARM definition](https://learn.microsoft.com/azure/templates/microsoft.cognitiveservices/2025-06-01/accounts/projects)
