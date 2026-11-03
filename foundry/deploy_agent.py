import argparse
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from azure.ai.projects import AIProjectClient
from azure.ai.projects.models import AgentVersionDetails, PromptAgentDefinition
from azure.identity import AzureCliCredential


@dataclass(frozen=True)
class AgentConfig:
    name: str
    description: str
    instructions: str


def load_agent(manifest: Path) -> AgentConfig:
    data = json.loads(manifest.read_text(encoding="utf-8"))
    name = data["name"]
    if not isinstance(name, str) or not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_-]{0,62}", name):
        raise ValueError("Agent name must contain 1-63 alphanumeric, underscore or hyphen characters.")
    description = data["description"]
    if not isinstance(description, str) or not description.strip():
        raise ValueError("Agent description must be non-empty.")
    instructions = (manifest.parent / data["instructions_file"]).read_text(encoding="utf-8")
    if not instructions.strip():
        raise ValueError("Agent instructions must be non-empty.")
    return AgentConfig(name=name, description=description, instructions=instructions)


def deploy_agent(
    project: AIProjectClient, config: AgentConfig, model: str, *, smoke_test: bool
) -> AgentVersionDetails:
    created = project.agents.create_version(
        agent_name=config.name,
        description=config.description,
        definition=PromptAgentDefinition(model=model, instructions=config.instructions, tools=[]),
    )
    agent = project.agents.get_version(agent_name=created.name, agent_version=created.version)
    print(f"Agent persisted: name={agent.name}, version={agent.version}, id={agent.id}")

    if smoke_test:
        with project.get_openai_client() as client:
            response = client.responses.create(
                input=(
                    "Przeanalizuj dokument: Interpelacja nr 123. "
                    "Posel pyta o termin remontu mostu. "
                    "W dokumencie nie podano odpowiedzi ani terminu remontu."
                ),
                extra_body={
                    "agent_reference": {
                        "type": "agent_reference",
                        "name": agent.name,
                        "version": agent.version,
                    }
                },
            )
            if response.status != "completed" or not response.output_text.strip():
                raise RuntimeError(
                    f"Agent smoke test failed: status={response.status}, error={response.error}"
                )
            print(f"Smoke test completed for {agent.name} version {agent.version}.")
            print(response.output_text)
    return agent


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a versioned Foundry prompt agent.")
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path(__file__).parent / "agents" / "sejm-analyst" / "agent.json",
    )
    parser.add_argument("--model", default=os.environ.get("AZURE_AI_MODEL_DEPLOYMENT_NAME"))
    parser.add_argument("--endpoint", default=os.environ.get("AZURE_AI_PROJECT_ENDPOINT"))
    parser.add_argument("--validate", action="store_true", help="Validate local files without Azure.")
    parser.add_argument("--smoke-test", action="store_true", help="Invoke the new version (billable).")
    args = parser.parse_args()

    if not args.model or not args.model.strip():
        parser.error("Set AZURE_AI_MODEL_DEPLOYMENT_NAME or pass --model.")
    config = load_agent(args.manifest)
    if args.validate:
        print(f"Valid agent: name={config.name}, model={args.model}")
        return

    endpoint = urlsplit(args.endpoint or "")
    if (
        endpoint.scheme != "https"
        or not endpoint.hostname
        or endpoint.username
        or endpoint.password
        or endpoint.query
        or endpoint.fragment
        or not re.fullmatch(r"/api/projects/[^/]+/?", endpoint.path)
    ):
        parser.error("Set AZURE_AI_PROJECT_ENDPOINT to an HTTPS Foundry project endpoint.")

    # Both local az login and GitHub's azure/login OIDC session use Azure CLI credentials.
    with (
        AzureCliCredential() as credential,
        AIProjectClient(endpoint=args.endpoint.rstrip("/"), credential=credential) as project,
    ):
        deploy_agent(project, config, args.model, smoke_test=args.smoke_test)


if __name__ == "__main__":
    main()
