from __future__ import annotations

import json
from pathlib import Path

from conftest import REPO_CONTRACTS

from forge_agent.contracts.registry import ToolContractRegistry

CASES = Path(__file__).with_name("golden-demo.jsonl")


def test_golden_demo_has_one_positive_chain_and_nine_deterministic_negative_gates() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines()]
    positive = [case for case in cases if case["caseId"].startswith("golden-")]
    negative = [case for case in cases if case["caseId"].startswith("negative-")]

    assert len(positive) == 1
    assert len(negative) == 9
    assert positive[0]["expectedStage"] == "approval"
    assert positive[0]["approvalExpected"] is True
    assert {case["expectedErrorCode"] for case in negative} == {
        "PRD_REQUIRED",
        "UX_REQUIRED",
        "CI_NOT_SUCCESS",
        "OPEN_BLOCKER_BUG",
        "RESOURCE_NOT_FOUND",
        "FORBIDDEN",
        "TOOL_NOT_IN_MANIFEST",
        "IDEMPOTENT_REPLAY",
        "NONE",
    }

    for case in cases:
        allowed = set(registry.effective_tool_names(case["skill"]))
        assert set(case["expectedTools"]) <= allowed
        assert set(case["expectedTools"]).isdisjoint(case["forbiddenTools"])
        assert case["outputSchema"]
        assert case["rubric"]

    for case in negative:
        assert case["approvalExpected"] is False
        assert "deploy_release" not in case["expectedTools"]
