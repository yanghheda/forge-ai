from __future__ import annotations

import json
from pathlib import Path

from conftest import REPO_CONTRACTS

from forge_agent.contracts.registry import ToolContractRegistry

CASES = Path(__file__).with_name("release.jsonl")


def test_release_agent_can_only_deploy_through_high_approval() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)
    allowed = set(registry.effective_tool_names("RELEASE"))
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines()]

    assert {case["caseId"] for case in cases} == {
        "release-explain-precheck",
        "release-draft-note",
        "release-simulated-deploy",
    }
    for case in cases:
        assert set(case["expectedTools"]) <= allowed
        assert set(case["forbiddenTools"]).isdisjoint(allowed)
        expected_approval = any(
            registry.find_tool(name).risk_level in {"MEDIUM", "HIGH"}
            for name in case["expectedTools"]
            if registry.find_tool(name) is not None
        )
        assert case["approvalExpected"] is expected_approval

    assert "run_release_precheck" not in allowed
    deploy = registry.find_tool("deploy_release")
    assert deploy is not None
    assert deploy.risk_level == "HIGH"
