import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from azure.ai.projects.models import AgentVersionDetails

from deploy_agent import AgentConfig, deploy_agent, load_agent, main


class AgentConfigTests(unittest.TestCase):
    def test_loads_instructions_relative_to_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "instructions.md").write_text("Analyze supplied documents.", encoding="utf-8")
            manifest = root / "agent.json"
            manifest.write_text(
                json.dumps(
                    {
                        "name": "example",
                        "description": "Example agent",
                        "instructions_file": "instructions.md",
                    }
                ),
                encoding="utf-8",
            )
            self.assertEqual(load_agent(manifest).instructions, "Analyze supplied documents.")
            (root / "instructions.md").write_text(" \n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "instructions must be non-empty"):
                load_agent(manifest)

    def test_missing_instructions_are_not_silently_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "agent.json"
            manifest.write_text(
                json.dumps(
                    {
                        "name": "example",
                        "description": "Example agent",
                        "instructions_file": "missing.md",
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaises(FileNotFoundError):
                load_agent(manifest)

    def test_missing_deployment_configuration_fails_before_authentication(self):
        for arguments in ([], ["--model", "example-model"]):
            with self.subTest(arguments=arguments):
                with (
                    patch.dict("os.environ", {}, clear=True),
                    patch("sys.argv", ["deploy_agent.py", *arguments]),
                    patch("deploy_agent.AzureCliCredential") as credential,
                    self.assertRaises(SystemExit) as error,
                ):
                    main()
                self.assertEqual(error.exception.code, 2)
                credential.assert_not_called()


class AgentDeploymentTests(unittest.TestCase):
    def setUp(self):
        self.config = AgentConfig("example", "Example agent", "Analyze supplied documents.")
        self.agent = AgentVersionDetails(
            {
                "id": "example:7",
                "name": "example",
                "version": "7",
                "definition": {"kind": "prompt", "model": "example-model"},
            }
        )
        self.project = Mock()
        self.project.agents.create_version.return_value = self.agent
        self.project.agents.get_version.return_value = self.agent
        self.client = Mock()
        self.project.get_openai_client.return_value.__enter__ = Mock(return_value=self.client)
        self.project.get_openai_client.return_value.__exit__ = Mock(return_value=False)

    def test_persists_definition_and_smoke_tests_the_exact_new_version(self):
        self.client.responses.create.return_value = Mock(
            status="completed", output_text="Streszczenie dokumentu."
        )
        result = deploy_agent(self.project, self.config, "example-model", smoke_test=True)
        self.assertEqual(result.version, "7")
        definition = self.project.agents.create_version.call_args.kwargs["definition"]
        self.assertEqual(
            definition.as_dict(),
            {
                "kind": "prompt",
                "model": "example-model",
                "instructions": self.config.instructions,
                "tools": [],
            },
        )
        self.project.agents.get_version.assert_called_once_with(
            agent_name="example", agent_version="7"
        )
        self.assertEqual(
            self.client.responses.create.call_args.kwargs["extra_body"]["agent_reference"],
            {"type": "agent_reference", "name": "example", "version": "7"},
        )

    def test_failed_or_empty_smoke_response_fails_deployment(self):
        for status, output in (("failed", ""), ("incomplete", "Partial"), ("completed", " ")):
            with self.subTest(status=status, output=output):
                self.client.responses.create.return_value = Mock(
                    status=status, output_text=output, error=None
                )
                with self.assertRaisesRegex(RuntimeError, "smoke test failed"):
                    deploy_agent(self.project, self.config, "example-model", smoke_test=True)

    def test_smoke_test_is_opt_in(self):
        deploy_agent(self.project, self.config, "example-model", smoke_test=False)
        self.project.get_openai_client.assert_not_called()

    def test_readback_errors_are_not_reported_as_success(self):
        self.project.agents.get_version.side_effect = RuntimeError("Readback failed")
        with self.assertRaisesRegex(RuntimeError, "Readback failed"):
            deploy_agent(self.project, self.config, "example-model", smoke_test=True)
        self.project.get_openai_client.assert_not_called()


if __name__ == "__main__":
    unittest.main()
