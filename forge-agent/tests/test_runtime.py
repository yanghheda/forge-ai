from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from forge_agent.gateway.runtime import (
    ContextManifest,
    FakeLanguageModel,
    LangGraphRuntimeGateway,
    RunStart,
    ToolSelection,
)
from forge_agent.gateway.tool_transport import StaticToolTransport, ToolExecution


def manifest(
    run_id: str = "01JTEST0000000000000000000",
    *,
    effective_tool_names: list[str] | None = None,
    max_tool_calls: int = 20,
) -> ContextManifest:
    return ContextManifest.model_validate(
        {
            "runId": run_id,
            "subject": {"userId": 12},
            "scope": {"workspaceId": 2, "projectId": 10, "workItemId": 1024},
            "skill": "UX",
            "effectiveToolNames": (
                effective_tool_names if effective_tool_names is not None else ["get_work_item"]
            ),
            "policy": {
                "mediumConfirmation": "ASK",
                "maxToolCalls": max_tool_calls,
                "tokenBudget": 50000,
            },
            "resourceRefs": [{"type": "WORK_ITEM", "id": "1024", "version": 7}],
            "expiresAt": datetime.now(UTC) + timedelta(minutes=5),
        }
    )


def test_fake_llm_graph_produces_plan_and_completion(tmp_path) -> None:
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), StaticToolTransport([])
    )

    result = runtime.start(RunStart(manifest=manifest(), message="整理 UX 交付计划"), "run-token")

    assert result.status == "SUCCEEDED"
    assert result.plan == ["理解 UX 请求", "整理受控上下文", "形成可回溯结果"]
    assert (
        result.answer
        == "Fake LLM completed UX plan with 1 resource reference(s) and 0 tool call(s)."
    )
    assert result.state_version >= 3
    assert result.tool_calls == []


def test_duplicate_start_returns_single_completed_execution(tmp_path) -> None:
    model = FakeLanguageModel()
    transport = StaticToolTransport([])
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", model, transport)
    request = RunStart(manifest=manifest(), message="整理 UX 交付计划")

    first = runtime.start(request, "run-token")
    repeated = runtime.start(request, "run-token")

    assert repeated == first
    assert model.plan_calls == 1
    assert model.finalize_calls == 1


def test_expired_manifest_is_rejected_before_graph_execution(tmp_path) -> None:
    expired = manifest().model_copy(update={"expires_at": datetime.now(UTC) - timedelta(seconds=1)})
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), StaticToolTransport([])
    )

    with pytest.raises(ValueError, match="manifest expired"):
        runtime.start(RunStart(manifest=expired, message="不能执行"), "run-token")


def test_new_runtime_recovers_from_checkpoint_after_node_failure(tmp_path) -> None:
    checkpoint_path = tmp_path / "checkpoints.sqlite"
    failing = FakeLanguageModel(fail_finalize_once=True)
    request = RunStart(manifest=manifest(), message="恢复运行")

    with pytest.raises(RuntimeError, match="injected finalize failure"):
        LangGraphRuntimeGateway(checkpoint_path, failing, StaticToolTransport([])).start(
            request, "run-token"
        )

    recovered_model = FakeLanguageModel()
    recovered = LangGraphRuntimeGateway(
        checkpoint_path, recovered_model, StaticToolTransport([])
    ).start(request, "run-token")

    assert recovered.status == "SUCCEEDED"
    assert recovered_model.plan_calls == 0
    assert recovered_model.finalize_calls == 1


def test_selected_tool_is_executed_and_observed_with_run_token(tmp_path) -> None:
    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="create_requirement",
                tool_call_id="call-1",
                replayed=False,
                result={"id": 42, "itemKey": "FORGE-42"},
            )
        ]
    )
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), transport
    )

    result = runtime.start(
        RunStart(
            manifest=manifest(
                effective_tool_names=["create_requirement", "get_work_item"],
                max_tool_calls=5,
            ),
            message="请创建需求",
        ),
        "run-scoped-token",
    )

    assert len(transport.requests) == 1
    request = transport.requests[0]
    assert request["tool_name"] == "create_requirement"
    assert request["tool_call_id"] == "call-1"
    assert request["arguments"] == {"title": "Fake 需求"}
    assert request["run_token"] == "run-scoped-token"

    assert len(result.tool_calls) == 1
    observed = result.tool_calls[0]
    assert observed.tool_name == "create_requirement"
    assert observed.tool_call_id == "call-1"
    assert observed.status == "SUCCEEDED"
    assert observed.result == {"id": 42, "itemKey": "FORGE-42"}
    assert result.answer.endswith("and 1 tool call(s).")


def test_multiple_tool_calls_receive_stable_distinct_ids(tmp_path) -> None:
    class TwoToolModel(FakeLanguageModel):
        def select_tool(self, skill, message, executed_tool_names):
            self.select_calls += 1
            if "get_project" not in executed_tool_names:
                return ToolSelection(tool_name="get_project")
            if "get_work_item" not in executed_tool_names:
                return ToolSelection(tool_name="get_work_item", arguments={"workItemId": 1024})
            return None

    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="get_project",
                tool_call_id="call-1",
                result={"id": 10},
            ),
            ToolExecution(
                status="SUCCEEDED",
                tool_name="get_work_item",
                tool_call_id="call-2",
                result={"id": 1024},
            ),
        ]
    )
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", TwoToolModel(), transport)

    result = runtime.start(
        RunStart(
            manifest=manifest(
                effective_tool_names=["get_project", "get_work_item"],
                max_tool_calls=2,
            ),
            message="读取项目和工作项",
        ),
        "run-token",
    )

    assert [request["tool_call_id"] for request in transport.requests] == ["call-1", "call-2"]
    assert [call.tool_call_id for call in result.tool_calls] == ["call-1", "call-2"]


def test_guard_rejects_tool_outside_manifest_allowlist_without_server_call(tmp_path) -> None:
    transport = StaticToolTransport([])
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), transport
    )

    result = runtime.start(
        RunStart(
            manifest=manifest(effective_tool_names=["get_work_item"], max_tool_calls=5),
            message="请创建需求",
        ),
        "run-token",
    )

    assert transport.requests == []
    assert len(result.tool_calls) == 1
    rejected = result.tool_calls[0]
    assert rejected.status == "REJECTED"
    assert rejected.error_code == "TOOL_NOT_IN_MANIFEST"


def test_guard_rejects_tool_when_call_budget_is_exhausted(tmp_path) -> None:
    transport = StaticToolTransport([])
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), transport
    )

    result = runtime.start(
        RunStart(
            manifest=manifest(effective_tool_names=["create_requirement"], max_tool_calls=0),
            message="请创建需求",
        ),
        "run-token",
    )

    assert transport.requests == []
    assert result.tool_calls[0].status == "REJECTED"
    assert result.tool_calls[0].error_code == "TOOL_CALL_BUDGET_EXCEEDED"


def test_waiting_approval_pauses_and_new_runtime_resumes_original_tool_call(tmp_path) -> None:
    checkpoint = tmp_path / "checkpoints.sqlite"
    transport = StaticToolTransport(
        [
            ToolExecution(
                status="WAITING_APPROVAL",
                tool_name="create_requirement",
                tool_call_id="call-1",
                approval_id="01JAPPROVAL000000000000000",
            )
        ]
    )
    runtime = LangGraphRuntimeGateway(checkpoint, FakeLanguageModel(), transport)
    request = RunStart(
        manifest=manifest(effective_tool_names=["create_requirement"], max_tool_calls=5),
        message="请创建需求",
    )

    paused = runtime.start(request, "first-token")

    assert paused.status == "WAITING_APPROVAL"
    assert paused.tool_calls == []
    assert len(transport.requests) == 1

    resumed_transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="create_requirement",
                tool_call_id="call-1",
                result={"id": 42},
            )
        ]
    )
    resumed = LangGraphRuntimeGateway(checkpoint, FakeLanguageModel(), resumed_transport).start(
        request, "renewed-token"
    )

    assert resumed.status == "SUCCEEDED"
    assert resumed_transport.requests[0]["tool_call_id"] == "call-1"
    assert resumed_transport.requests[0]["run_token"] == "renewed-token"
    assert len(resumed.tool_calls) == 1


def test_transport_failure_is_normalized_to_failed_observation(tmp_path) -> None:
    class FailingTransport:
        def execute(self, tool_name, tool_call_id, arguments, run_token):
            raise RuntimeError("connection refused")

    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), FailingTransport()
    )

    result = runtime.start(
        RunStart(
            manifest=manifest(effective_tool_names=["create_requirement"], max_tool_calls=5),
            message="请创建需求",
        ),
        "run-token",
    )

    assert len(result.tool_calls) == 1
    failed = result.tool_calls[0]
    assert failed.status == "FAILED"
    assert failed.error_code == "TRANSPORT_ERROR"
    assert "connection refused" in (failed.error_message or "")
    assert result.status == "SUCCEEDED"


def test_tool_calls_are_not_replayed_after_completed_run(tmp_path) -> None:
    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="create_requirement",
                tool_call_id="call-1",
                replayed=False,
                result={"id": 42},
            )
        ]
    )
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", FakeLanguageModel(), transport
    )
    request = RunStart(
        manifest=manifest(effective_tool_names=["create_requirement"], max_tool_calls=5),
        message="请创建需求",
    )

    first = runtime.start(request, "run-token")
    repeated = runtime.start(request, "run-token")

    assert len(transport.requests) == 1
    assert repeated.tool_calls == first.tool_calls
    assert repeated.tool_calls[0].replayed is False
