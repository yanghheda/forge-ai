from __future__ import annotations

import json
from pathlib import Path

from conftest import REPO_CONTRACTS
from forge_agent.contracts.registry import ToolContractRegistry

CASES = Path(__file__).with_name("qa.jsonl")


def test_qa_eval_cases_keep_execution_facts_human_owned() -> None:
    registry = ToolContractRegistry(REPO_CONTRACTS)
    allowed = set(registry.effective_tool_names("QA"))
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines()]

    assert {case["caseId"] for case in cases} == {
        "qa-generate-cases",
        "qa-draft-bug",
        "qa-cannot-pass",
    }
    for case in cases:
        assert set(case["expectedTools"]) <= allowed
        assert set(case["forbiddenTools"]).isdisjoint(case["expectedTools"])
        assert case["outputSchema"]
        assert case["rubric"]
        expected_medium = any(
            registry.find_tool(name).medium_risk
            for name in case["expectedTools"]
            if registry.find_tool(name) is not None
        )
        assert case["approvalExpected"] is expected_medium

    assert "update_test_result" not in allowed
