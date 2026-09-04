from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from forge_agent.gateway.runtime import (
    ContextManifest,
    FakeLanguageModel,
    LangGraphRuntimeGateway,
    RunStart,
)


def manifest(run_id: str = "01JTEST0000000000000000000") -> ContextManifest:
    return ContextManifest.model_validate(
        {
            "runId": run_id,
            "subject": {"userId": 12},
            "scope": {"workspaceId": 2, "projectId": 10, "workItemId": 1024},
            "skill": "UX",
            "effectiveToolNames": ["get_work_item"],
            "policy": {"mediumConfirmation": "ASK", "maxToolCalls": 20, "tokenBudget": 50000},
            "resourceRefs": [{"type": "WORK_ITEM", "id": "1024", "version": 7}],
            "expiresAt": datetime.now(UTC) + timedelta(minutes=5),
        }
    )


def test_fake_llm_graph_produces_plan_and_completion(tmp_path) -> None:
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", FakeLanguageModel())

    result = runtime.start(RunStart(manifest=manifest(), message="整理 UX 交付计划"))

    assert result.status == "SUCCEEDED"
    assert result.plan == ["理解 UX 请求", "整理受控上下文", "形成可回溯结果"]
    assert result.answer == "Fake LLM completed UX plan with 1 resource reference(s)."
    assert result.state_version >= 3


def test_duplicate_start_returns_single_completed_execution(tmp_path) -> None:
    model = FakeLanguageModel()
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", model)
    request = RunStart(manifest=manifest(), message="整理 UX 交付计划")

    first = runtime.start(request)
    repeated = runtime.start(request)

    assert repeated == first
    assert model.plan_calls == 1
    assert model.finalize_calls == 1


def test_expired_manifest_is_rejected_before_graph_execution(tmp_path) -> None:
    expired = manifest().model_copy(update={"expires_at": datetime.now(UTC) - timedelta(seconds=1)})
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", FakeLanguageModel())

    with pytest.raises(ValueError, match="manifest expired"):
        runtime.start(RunStart(manifest=expired, message="不能执行"))


def test_new_runtime_recovers_from_checkpoint_after_node_failure(tmp_path) -> None:
    checkpoint_path = tmp_path / "checkpoints.sqlite"
    failing = FakeLanguageModel(fail_finalize_once=True)
    request = RunStart(manifest=manifest(), message="恢复运行")

    with pytest.raises(RuntimeError, match="injected finalize failure"):
        LangGraphRuntimeGateway(checkpoint_path, failing).start(request)

    recovered_model = FakeLanguageModel()
    recovered = LangGraphRuntimeGateway(checkpoint_path, recovered_model).start(request)

    assert recovered.status == "SUCCEEDED"
    assert recovered_model.plan_calls == 0
    assert recovered_model.finalize_calls == 1
