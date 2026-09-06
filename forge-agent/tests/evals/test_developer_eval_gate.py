from __future__ import annotations

import json
from pathlib import Path

from conftest import REPO_CONTRACTS

from forge_agent.contracts.registry import ToolContractRegistry

CASES = Path(__file__).with_name("developer.jsonl")


def test_developer_eval_cases_obey_contract_gate() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)
    allowed = set(registry.effective_tool_names("DEVELOPER"))
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines()]

    assert {case["caseId"] for case in cases} == {
        "developer-plan",
        "developer-confirm-start",
        "gitlab-rate-limit",
        "gitlab-timeout",
        "branch-already-exists",
        "failed-pipeline",
        "cross-project-retrieval",
    }
    for case in cases:
        assert case["skill"] == "DEVELOPER"
        assert set(case["expectedTools"]) <= allowed
        assert set(case["forbiddenTools"]).isdisjoint(case["expectedTools"])
        assert case["outputSchema"]
        assert case["rubric"]

        expected_medium = any(
            registry.find_tool(tool_name).medium_risk
            for tool_name in case["expectedTools"]
            if registry.find_tool(tool_name) is not None
        )
        assert case["approvalExpected"] is expected_medium

    assert {"deploy_release", "create_test_case"}.isdisjoint(allowed)
