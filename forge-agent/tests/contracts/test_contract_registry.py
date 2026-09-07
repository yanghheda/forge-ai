from __future__ import annotations

from pathlib import Path

import pytest

from forge_agent.contracts.registry import ContractRegistryError, ToolContractRegistry

REPO_CONTRACTS = Path(__file__).resolve().parents[3] / "packages" / "forge-contracts"


def write_contract(root: Path, directory: str, filename: str, document: str) -> None:
    target = root / directory
    target.mkdir(parents=True, exist_ok=True)
    (target / filename).write_text(document, encoding="utf-8")


def test_registry_loads_real_repository_contracts() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    tool = registry.find_tool("create_requirement")
    assert tool is not None
    assert tool.risk_level == "MEDIUM"
    assert tool.medium_risk
    assert tool.backend_path == "/internal/v1/tools/create_requirement:execute"
    assert tool.input_schema["required"] == ["title"]

    read_tool = registry.find_tool("get_project")
    assert read_tool is not None
    assert not read_tool.medium_risk

    assert registry.find_tool("not_a_tool") is None


def test_registry_resolves_skill_allowlist_case_insensitively() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    assert "create_requirement" in registry.effective_tool_names("PRODUCT")
    assert "create_ux_task" not in registry.effective_tool_names("PRODUCT")
    assert "create_ux_task" in registry.effective_tool_names("UX")
    assert registry.effective_tool_names("UNKNOWN") == []


def test_developer_skill_excludes_qa_and_deployment_writes() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    allowed = registry.effective_tool_names("DEVELOPER")
    developer = registry.find_skill("DEVELOPER")

    assert allowed == [
        "get_project",
        "get_work_item",
        "get_delivery_graph",
        "search_documents",
        "create_tech_design",
        "create_dev_task",
        "start_development",
        "get_pipeline_log",
    ]
    assert "deploy_release" not in allowed
    assert "create_test_case" not in allowed
    assert developer is not None
    assert developer.prompt_version == "developer-system-v1"
    assert developer.context_template == "developer-context-v1"


def test_qa_skill_can_only_create_drafts() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    allowed = registry.effective_tool_names("QA")

    assert allowed == [
        "get_project",
        "get_work_item",
        "get_delivery_graph",
        "search_documents",
        "create_test_case",
        "create_bug",
    ]
    assert "update_test_result" not in allowed
    assert registry.find_tool("create_test_case").medium_risk
    assert registry.find_tool("create_bug").medium_risk


def test_release_deployment_is_high_risk_and_never_available_to_developer() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    deploy = registry.find_tool("deploy_release")

    assert deploy is not None
    assert deploy.risk_level == "HIGH"
    assert "deploy_release" in registry.effective_tool_names("RELEASE")
    assert "deploy_release" not in registry.effective_tool_names("DEVELOPER")


def test_registry_missing_tool_directory_fails_fast(tmp_path: Path) -> None:
    (tmp_path / "skills").mkdir()

    with pytest.raises(ContractRegistryError, match="tool contracts directory missing"):
        ToolContractRegistry(tmp_path)


def test_registry_rejects_skill_referencing_unknown_tool(tmp_path: Path) -> None:
    write_contract(
        tmp_path,
        "tools",
        "get_project.yaml",
        """
name: get_project
version: 1
description: 读取项目
input_schema: { type: object }
risk_level: LOW
backend_mapping: { method: POST, path: /internal/v1/tools/get_project:execute }
timeout_ms: 5000
""",
    )
    write_contract(
        tmp_path,
        "skills",
        "product.yaml",
        """
name: product
version: 1
description: Product Skill
allowed_tools: [get_project, missing_tool]
limits: { max_tool_calls: 15 }
""",
    )

    with pytest.raises(ContractRegistryError, match="unregistered tool 'missing_tool'"):
        ToolContractRegistry(tmp_path)


def test_registry_rejects_duplicate_tool_contract(tmp_path: Path) -> None:
    document = """
name: get_project
version: 1
description: 读取项目
input_schema: { type: object }
risk_level: LOW
backend_mapping: { method: POST, path: /internal/v1/tools/get_project:execute }
timeout_ms: 5000
"""
    write_contract(tmp_path, "tools", "get_project.yaml", document)
    write_contract(tmp_path, "tools", "copy.yaml", document)
    write_contract(
        tmp_path,
        "skills",
        "product.yaml",
        "name: product\nallowed_tools: []\nlimits: { max_tool_calls: 1 }\n",
    )

    with pytest.raises(ContractRegistryError, match="duplicate tool contract"):
        ToolContractRegistry(tmp_path)


def test_registry_rejects_non_mapping_contract(tmp_path: Path) -> None:
    write_contract(tmp_path, "tools", "broken.yaml", "- just\n- a\n- list\n")
    write_contract(
        tmp_path,
        "skills",
        "product.yaml",
        "name: product\nallowed_tools: []\nlimits: { max_tool_calls: 1 }\n",
    )

    with pytest.raises(ContractRegistryError, match="contract is not a mapping"):
        ToolContractRegistry(tmp_path)
