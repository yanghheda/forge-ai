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
    assert tool.input_schema["required"] == ["title", "description"]
    assert tool.input_schema["properties"]["description"]["minLength"] == 20

    read_tool = registry.find_tool("get_organization")
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
        "get_organization",
        "get_work_item",
        "list_requirements",
        "list_tasks",
        "list_bugs",
        "list_members",
        "get_delivery_graph",
        "search_documents",
        "create_tech_design",
        "create_dev_task",
        "start_development",
        "get_pipeline_log",
        "update_task",
        "advance_task",
        "update_bug",
        "advance_bug",
        "advance_requirement",
    ]
    assert "deploy_release" not in allowed
    assert "create_test_case" not in allowed
    assert developer is not None
    assert developer.prompt_version == "developer-system-v1"
    assert developer.context_template == "developer-context-v1"


def test_qa_skill_can_run_tests_but_conclusions_require_confirmation() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    allowed = registry.effective_tool_names("QA")

    assert allowed == [
        "get_organization",
        "get_work_item",
        "list_requirements",
        "list_tasks",
        "list_bugs",
        "list_members",
        "get_test_run",
        "get_delivery_graph",
        "search_documents",
        "create_test_case",
        "create_qa_task",
        "create_bug",
        "update_task",
        "advance_task",
        "update_bug",
        "advance_bug",
        "start_test_run",
        "record_test_result",
        "complete_test_run",
        "reopen_test_run",
        "advance_requirement",
    ]
    assert "update_test_result" not in allowed
    assert registry.find_tool("create_test_case").medium_risk
    # 测试任务与缺陷创建同为 MEDIUM 写操作，可由 QA Skill 在 Run 策略允许时执行（ADR-023 §3）。
    assert registry.find_tool("create_qa_task").medium_risk
    assert registry.find_tool("create_bug").medium_risk
    # 测试执行对 QA 开放，但执行结论与运行收尾必须强制人工确认后才落库（ADR-023 §3）。
    for name in ("record_test_result", "complete_test_run"):
        contract = registry.find_tool(name)
        assert contract is not None
        assert contract.requires_confirmation
        assert contract.medium_risk
        assert contract.forced_confirmation
    # 创建测试运行只是建立执行容器，可以按 Run 策略自动执行。
    start_run = registry.find_tool("start_test_run")
    assert start_run is not None
    assert start_run.medium_risk
    assert not start_run.requires_confirmation
    # 重开已完成的测试运行是受控写操作，但可由 QA Agent 在 Run 策略允许时直接执行。
    reopen_run = registry.find_tool("reopen_test_run")
    assert reopen_run is not None
    assert reopen_run.medium_risk
    assert not reopen_run.requires_confirmation


def test_every_skill_declares_stage_guidance() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)

    for skill_name in ("PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE"):
        skill = registry.find_skill(skill_name)
        assert skill is not None
        # 阶段能力说明会被注入模型提示词，用于约束本阶段可做什么与哪些动作属人工专属。
        assert skill.guidance is not None
        assert "人工专属" in skill.guidance


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
        "get_organization.yaml",
        """
name: get_organization
version: 1
description: 读取项目
input_schema: { type: object }
risk_level: LOW
backend_mapping: { method: POST, path: /internal/v1/tools/get_organization:execute }
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
allowed_tools: [get_organization, missing_tool]
limits: { max_tool_calls: 15 }
""",
    )

    with pytest.raises(ContractRegistryError, match="unregistered tool 'missing_tool'"):
        ToolContractRegistry(tmp_path)


def test_registry_rejects_duplicate_tool_contract(tmp_path: Path) -> None:
    document = """
name: get_organization
version: 1
description: 读取项目
input_schema: { type: object }
risk_level: LOW
backend_mapping: { method: POST, path: /internal/v1/tools/get_organization:execute }
timeout_ms: 5000
"""
    write_contract(tmp_path, "tools", "get_organization.yaml", document)
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
