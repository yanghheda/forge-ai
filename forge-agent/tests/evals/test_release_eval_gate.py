from __future__ import annotations

import json
from pathlib import Path

from conftest import REPO_CONTRACTS
from forge_agent.contracts.registry import ToolContractRegistry

CASES = Path(__file__).with_name("release.jsonl")


def test_release_agent_cannot_decide_precheck_or_deploy() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)
    allowed = set(registry.effective_tool_names("RELEASE"))
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines()]

    assert {case["caseId"] for case in cases} == {
        "release-explain-precheck",
        "release-draft-note",
    }
    for case in cases:
        assert set(case["expectedTools"]) <= allowed
        assert set(case["forbiddenTools"]).isdisjoint(allowed)
        expected_medium = any(
            registry.find_tool(name).medium_risk
            for name in case["expectedTools"]
            if registry.find_tool(name) is not None
        )
        assert case["approvalExpected"] is expected_medium

    assert "run_release_precheck" not in allowed
    assert "deploy_release" not in allowed
