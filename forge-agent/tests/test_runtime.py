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
from forge_agent.gateway.tool_transport import (
    StaticToolTransport,
    ToolExecution,
    ToolTransportFailure,
)


class RecordingAnswerStream:
    def __init__(self) -> None:
        self.deltas: list[tuple[str, str, str]] = []
        self.reasoning: list[tuple[str, str, str]] = []

    def publish(self, run_id: str, delta: str, run_token: str) -> None:
        self.deltas.append((run_id, delta, run_token))

    def publish_reasoning(self, run_id: str, delta: str, run_token: str) -> None:
        self.reasoning.append((run_id, delta, run_token))


class FailingAnswerStream:
    def publish(self, run_id: str, delta: str, run_token: str) -> None:
        raise ToolTransportFailure("answer stream publish failed")

    def publish_reasoning(self, run_id: str, delta: str, run_token: str) -> None:
        raise ToolTransportFailure("reasoning stream publish failed")


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
            "scope": {"organizationId": 1, "workItemId": 1024},
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


def test_runtime_publishes_each_final_answer_delta(tmp_path) -> None:
    answer_stream = RecordingAnswerStream()
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite",
        FakeLanguageModel(),
        StaticToolTransport([]),
        answer_stream,
    )

    result = runtime.start(RunStart(manifest=manifest(), message="整理 UX 交付计划"), "run-token")

    assert "".join(delta for _, delta, _ in answer_stream.deltas) == result.answer
    assert len(answer_stream.deltas) == 2
    assert all(run_id == manifest().run_id for run_id, _, _ in answer_stream.deltas)
    assert all(token == "run-token" for _, _, token in answer_stream.deltas)
    assert "\n".join(delta for _, delta, _ in answer_stream.reasoning) == "\n".join(result.plan)


def test_runtime_completes_when_replayable_answer_delta_publish_fails(tmp_path) -> None:
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite",
        FakeLanguageModel(),
        StaticToolTransport([]),
        FailingAnswerStream(),
    )

    result = runtime.start(RunStart(manifest=manifest(), message="新建需求"), "run-token")

    assert result.status == "SUCCEEDED"
    assert result.answer


def test_fake_model_recognizes_natural_create_requirement_phrase() -> None:
    model = FakeLanguageModel()

    selection = model.select_tool("PRODUCT", "创建一个新的需求，名称随意", None, [], [])

    assert selection is not None
    assert selection.tool_name == "create_requirement"
    assert (
        selection.arguments["description"] == "用于验证 ForgeAI Fake Agent 黄金流程的确定性需求。"
    )


def test_fake_model_treats_unbound_product_new_command_as_requirement_creation() -> None:
    model = FakeLanguageModel()

    selection = model.select_tool("PRODUCT", "新建", None, [], [])

    assert selection is not None
    assert selection.tool_name == "create_requirement"


def test_fake_product_creates_prd_for_bound_requirement() -> None:
    selection = FakeLanguageModel().select_tool("PRODUCT", "创建 PRD", 42, [], [])

    assert selection == ToolSelection(
        tool_name="create_prd_document",
        arguments={
            "requirementId": 42,
            "title": "Fake PRD",
            "contentMarkdown": (
                "# 1 背景与目标\n\n"
                "为当前需求创建可编辑的产品需求文档。\n\n"
                "## 2 验收标准\n\n"
                "- PRD 正文已保存为初始版本"
            ),
        },
    )


def test_fake_stage_progress_reads_version_then_uses_authoritative_action() -> None:
    model = FakeLanguageModel()
    read = model.select_tool("PRODUCT", "推进当前阶段", 42, [], [])
    advance = model.select_tool(
        "PRODUCT",
        "推进当前阶段",
        42,
        [],
        [
            {
                "toolName": "get_work_item",
                "status": "SUCCEEDED",
                "result": {"id": 42, "status": "DRAFT", "version": 7},
            }
        ],
    )

    assert read == ToolSelection(tool_name="get_work_item", arguments={"workItemId": 42})
    assert advance == ToolSelection(
        tool_name="advance_requirement",
        arguments={
            "requirementId": 42,
            "action": "SUBMIT_PRODUCT_REVIEW",
            "expectedVersion": 7,
        },
    )


def test_fake_ux_progress_uses_server_checklist_contract_keys() -> None:
    selection = FakeLanguageModel().select_tool(
        "UX",
        "推进当前阶段",
        42,
        [],
        [
            {
                "toolName": "get_work_item",
                "status": "SUCCEEDED",
                "result": {"id": 42, "status": "UX_IN_PROGRESS", "version": 3},
            }
        ],
    )

    assert selection == ToolSelection(
        tool_name="advance_requirement",
        arguments={
            "requirementId": 42,
            "action": "SUBMIT_UX_REVIEW",
            "expectedVersion": 3,
            "checklist": ["userFlow", "pageList", "keyInteraction", "exceptionState"],
        },
    )


def test_fake_ux_creates_task_and_spec_from_explicit_commands() -> None:
    model = FakeLanguageModel()

    task = model.select_tool("UX", "创建 UX 任务", 42, [], [])
    document = model.select_tool("UX", "创建 UX 文档", 42, [], [])

    assert task == ToolSelection(
        tool_name="create_ux_task",
        arguments={"requirementId": 42, "title": "Fake UX Task"},
    )
    assert document == ToolSelection(
        tool_name="create_ux_document",
        arguments={"requirementId": 42, "title": "Fake UX Spec"},
    )


@pytest.mark.parametrize(
    ("skill", "message", "tool_name", "arguments"),
    [
        (
            "DEVELOPER",
            "创建技术设计",
            "create_tech_design",
            {"requirementId": 42, "title": "Fake Tech Design"},
        ),
        (
            "DEVELOPER",
            "创建开发任务",
            "create_dev_task",
            {"requirementId": 42, "title": "Fake Dev Task"},
        ),
        (
            "QA",
            "创建测试用例",
            "create_test_case",
            {
                "requirementId": 42,
                "title": "Fake Test Case",
                "steps": ["执行黄金流程操作"],
                "expectedResult": "操作符合验收预期",
                "priority": "P1",
            },
        ),
        (
            "QA",
            "创建缺陷",
            "create_bug",
            {
                "requirementId": 42,
                "title": "Fake Bug",
                "severity": "MAJOR",
                "reproductionSteps": ["执行黄金流程操作"],
                "expectedResult": "操作成功",
                "actualResult": "操作失败",
            },
        ),
        (
            "RELEASE",
            "创建发布",
            "create_release",
            {
                "versionName": "fake-golden-release",
                "environment": "staging-simulated",
                "requirementIds": [42],
                "approvalTtlMinutes": 60,
            },
        ),
    ],
)
def test_fake_routes_explicit_golden_flow_asset_commands(
    skill, message, tool_name, arguments
) -> None:
    selection = FakeLanguageModel().select_tool(skill, message, 42, [], [])

    assert selection == ToolSelection(tool_name=tool_name, arguments=arguments)


def test_fake_release_chains_creation_into_backend_precheck() -> None:
    model = FakeLanguageModel()
    create = model.select_tool("RELEASE", "创建发布并预检", 42, [], [])
    precheck = model.select_tool(
        "RELEASE",
        "创建发布并预检",
        42,
        [],
        [
            {
                "toolName": "create_release",
                "status": "SUCCEEDED",
                "result": {"id": 9, "version": 0},
            }
        ],
    )

    assert create is not None and create.tool_name == "create_release"
    assert precheck == ToolSelection(tool_name="run_release_precheck", arguments={"releaseId": 9})


def test_fake_model_does_not_claim_requirement_created_without_tool_result() -> None:
    model = FakeLanguageModel()

    answer = model.finalize("PRODUCT", "创建一个新需求", 0, [])

    assert answer == "未执行 create_requirement Tool，需求尚未创建。"


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
    assert request["arguments"] == {
        "title": "Fake 需求",
        "description": "用于验证 ForgeAI Fake Agent 黄金流程的确定性需求。",
    }
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
        def select_tool(self, skill, message, work_item_id, tool_definitions, tool_calls):
            self.select_calls += 1
            executed_tool_names = [call.get("toolName") for call in tool_calls]
            if "get_organization" not in executed_tool_names:
                return ToolSelection(tool_name="get_organization")
            if "get_work_item" not in executed_tool_names:
                return ToolSelection(tool_name="get_work_item", arguments={"workItemId": 1024})
            return None

    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="get_organization",
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
                effective_tool_names=["get_organization", "get_work_item"],
                max_tool_calls=2,
            ),
            message="读取项目和工作项",
        ),
        "run-token",
    )

    assert [request["tool_call_id"] for request in transport.requests] == ["call-1", "call-2"]
    assert [call.tool_call_id for call in result.tool_calls] == ["call-1", "call-2"]


def test_successful_prd_write_completes_without_another_model_decision(tmp_path) -> None:
    class PrdModel(FakeLanguageModel):
        def select_tool(self, skill, message, work_item_id, tool_definitions, tool_calls):
            self.select_calls += 1
            if not tool_calls:
                return ToolSelection(
                    tool_name="search_documents",
                    arguments={"query": "需求背景"},
                )
            if len(tool_calls) == 1:
                return ToolSelection(
                    tool_name="create_prd_document",
                    arguments={
                        "requirementId": work_item_id,
                        "title": "生成的 PRD",
                        "contentMarkdown": "# 背景与目标\n\n生成完整且可验证的 PRD 正文。",
                    },
                )
            raise RuntimeError("successful PRD write must not request another model decision")

    model = PrdModel()
    answer_stream = RecordingAnswerStream()
    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="search_documents",
                tool_call_id="call-1",
                result={"items": []},
            ),
            ToolExecution(
                status="SUCCEEDED",
                tool_name="create_prd_document",
                tool_call_id="call-2",
                result={"id": 9, "currentVersionNo": 1},
            ),
        ]
    )
    request_manifest = manifest(
        effective_tool_names=["search_documents", "create_prd_document"],
        max_tool_calls=5,
    ).model_copy(update={"skill": "PRODUCT"})
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", model, transport, answer_stream
    )

    result = runtime.start(
        RunStart(manifest=request_manifest, message="检索背景并填充 PRD 正文"),
        "run-token",
    )

    assert result.status == "SUCCEEDED"
    assert model.select_calls == 2
    assert model.finalize_calls == 0
    assert [call.tool_name for call in result.tool_calls] == [
        "search_documents",
        "create_prd_document",
    ]
    assert result.answer == "PRD 正文已保存（文档 9，版本 1）。"
    assert [delta for _, delta, _ in answer_stream.deltas] == [result.answer]


def test_stage_agent_reads_authoritative_version_before_requesting_transition(tmp_path) -> None:
    class StageProgressModel(FakeLanguageModel):
        def select_tool(self, skill, message, work_item_id, tool_definitions, tool_calls):
            self.select_calls += 1
            if not tool_calls:
                return ToolSelection(
                    tool_name="get_work_item", arguments={"workItemId": work_item_id}
                )
            if len(tool_calls) == 1:
                version = tool_calls[0]["result"]["version"]
                return ToolSelection(
                    tool_name="advance_requirement",
                    arguments={
                        "requirementId": work_item_id,
                        "action": "SUBMIT_FOR_QA",
                        "expectedVersion": version,
                    },
                )
            raise RuntimeError("successful transition must not request another model decision")

    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="get_work_item",
                tool_call_id="call-1",
                result={"id": 1024, "status": "IN_DEVELOPMENT", "version": 7},
            ),
            ToolExecution(
                status="SUCCEEDED",
                tool_name="advance_requirement",
                tool_call_id="call-2",
                result={"status": "READY_FOR_QA", "version": 8},
            ),
        ]
    )
    request_manifest = manifest(
        effective_tool_names=["get_work_item", "advance_requirement"], max_tool_calls=5
    ).model_copy(update={"skill": "DEVELOPER"})
    model = StageProgressModel()
    answer_stream = RecordingAnswerStream()
    runtime = LangGraphRuntimeGateway(
        tmp_path / "checkpoints.sqlite", model, transport, answer_stream
    )

    result = runtime.start(
        RunStart(manifest=request_manifest, message="检查并提交 QA"), "run-token"
    )

    assert result.status == "SUCCEEDED"
    assert model.select_calls == 2
    assert model.finalize_calls == 0
    assert transport.requests[1]["arguments"]["expectedVersion"] == 7
    assert result.tool_calls[1].result == {"status": "READY_FOR_QA", "version": 8}
    assert result.answer == "当前阶段已推进（状态 READY_FOR_QA，版本 8）。"
    assert [delta for _, delta, _ in answer_stream.deltas] == [result.answer]


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


def test_stubborn_model_stops_after_tool_budget_rejection(tmp_path) -> None:
    class StubbornModel(FakeLanguageModel):
        def select_tool(self, skill, message, work_item_id, tool_definitions, tool_calls):
            self.select_calls += 1
            return ToolSelection(
                tool_name="search_documents",
                arguments={"query": "重复检索"},
            )

    model = StubbornModel()
    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="search_documents",
                tool_call_id="call-1",
                result={"items": []},
            )
        ]
    )
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", model, transport)

    result = runtime.start(
        RunStart(
            manifest=manifest(effective_tool_names=["search_documents"], max_tool_calls=1),
            message="持续检索",
        ),
        "run-token",
    )

    assert result.status == "SUCCEEDED"
    assert len(transport.requests) == 1
    assert [call.status for call in result.tool_calls] == ["SUCCEEDED", "REJECTED"]
    assert result.tool_calls[-1].error_code == "TOOL_CALL_BUDGET_EXCEEDED"
    assert model.select_calls == 2


def test_graph_recursion_limit_scales_with_tool_call_budget(tmp_path) -> None:
    class BoundedModel(FakeLanguageModel):
        def select_tool(self, skill, message, work_item_id, tool_definitions, tool_calls):
            self.select_calls += 1
            if len(tool_calls) < 7:
                return ToolSelection(
                    tool_name="search_documents",
                    arguments={"query": f"检索 {len(tool_calls) + 1}"},
                )
            return None

    model = BoundedModel()
    transport = StaticToolTransport(
        [
            ToolExecution(
                status="SUCCEEDED",
                tool_name="search_documents",
                tool_call_id=f"call-{index}",
                result={"items": []},
            )
            for index in range(1, 8)
        ]
    )
    runtime = LangGraphRuntimeGateway(tmp_path / "checkpoints.sqlite", model, transport)

    result = runtime.start(
        RunStart(
            manifest=manifest(effective_tool_names=["search_documents"], max_tool_calls=7),
            message="执行有界多轮检索",
        ),
        "run-token",
    )

    assert result.status == "SUCCEEDED"
    assert len(result.tool_calls) == 7
    assert model.select_calls == 8


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
    assert result.answer == (
        "工具 create_requirement 执行失败（TRANSPORT_ERROR）：connection refused；"
        "业务操作未完成，请查看 Tool Trace 并修复后重试。"
    )


def test_fake_model_reports_guard_rejection_instead_of_claiming_completion() -> None:
    answer = FakeLanguageModel().finalize(
        "PRODUCT",
        "推进当前阶段",
        1,
        [
            {
                "toolName": "get_work_item",
                "status": "SUCCEEDED",
                "result": {"status": "DRAFT", "version": 2},
            },
            {
                "toolName": "advance_requirement",
                "status": "FAILED",
                "errorCode": "WORKFLOW_GUARD_FAILED",
                "errorMessage": "Published PRD is required",
            },
        ],
    )

    assert answer == (
        "工具 advance_requirement 执行失败（WORKFLOW_GUARD_FAILED）：Published PRD is required；"
        "业务操作未完成，请查看 Tool Trace 并修复后重试。"
    )


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
