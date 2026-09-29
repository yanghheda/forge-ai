package ai.forge.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.application.AgentTraceAggregator.Approval;
import ai.forge.server.agent.application.AgentTraceAggregator.NodeKind;
import ai.forge.server.agent.application.AgentTraceAggregator.ToolCall;
import ai.forge.server.agent.domain.AgentEvent;
import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.AgentStep;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTraceAggregatorTest {

    private static final String RUN_ID = "01M2FBKQYQ6QNJ8FZH92PZP0XY";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FINISH = START.plusSeconds(4);

    @Test
    void mergesModelThinkingAndSplitsWhenSourceNodeChanges() {
        List<TraceNode> nodes = AgentTraceAggregator.aggregate(
                List.of(
                        event(1, "thinking.delta", Map.of("delta", "第一段", "node", "create_plan")),
                        event(2, "thinking.delta", Map.of("delta", "第二段", "node", "create_plan")),
                        event(3, "thinking.delta", Map.of("delta", "换节点", "node", "select_tool")),
                        event(4, "reasoning.delta", Map.of("delta", "思路一")),
                        event(5, "reasoning.delta", Map.of("delta", "思路二"))),
                List.of(), List.of(), List.of(), run(AgentRunStatus.RUNNING));

        assertThat(nodes).extracting(TraceNode::kind)
                .containsExactly(NodeKind.THINKING, NodeKind.THINKING, NodeKind.REASONING);
        assertThat(nodes.get(0).key()).isEqualTo("seq:1");
        assertThat(nodes.get(0).title()).isEqualTo("模型思考 · 计划");
        assertThat(nodes.get(0).detail().get("text")).isEqualTo("第一段第二段");
        assertThat(nodes.get(0).detail().get("node")).isEqualTo("create_plan");
        assertThat(nodes.get(1).title()).isEqualTo("模型思考 · 选择工具");
        assertThat(nodes.get(1).detail().get("text")).isEqualTo("换节点");
        assertThat(nodes.get(2).title()).isEqualTo("执行思路");
        assertThat(nodes.get(2).detail().get("text")).isEqualTo("思路一思路二");
    }

    @Test
    void keepsUnknownEventsAsFallbackNodesAndComputesTerminalDuration() {
        List<TraceNode> nodes = AgentTraceAggregator.aggregate(
                List.of(
                        event(3, "plan.created", Map.of("steps", List.of("步骤A", "步骤B"))),
                        event(5, "custom.brand.new-event", Map.of("value", 9, "flag", true)),
                        event(9, "agent.completed", Map.of("status", "SUCCEEDED", "summary", "已完成"))),
                List.of(), List.of(), List.of(), run(AgentRunStatus.SUCCEEDED));

        assertThat(nodes).extracting(TraceNode::key).containsExactly("seq:3", "seq:5", "seq:9");
        assertThat(nodes.get(0).kind()).isEqualTo(NodeKind.PLAN);
        assertThat(nodes.get(0).detail().get("steps")).isEqualTo(List.of("步骤A", "步骤B"));
        assertThat(nodes.get(1).kind()).isEqualTo(NodeKind.EVENT);
        assertThat(nodes.get(1).title()).isEqualTo("custom.brand.new-event");
        assertThat(nodes.get(1).detail().get("eventType")).isEqualTo("custom.brand.new-event");
        assertThat(nodes.get(1).detail().get("payload")).isEqualTo(Map.of("value", 9, "flag", true));
        assertThat(nodes.get(2).kind()).isEqualTo(NodeKind.TERMINAL);
        assertThat(nodes.get(2).status()).isEqualTo("SUCCEEDED");
        assertThat(nodes.get(2).detail().get("summary")).isEqualTo("已完成");
        assertThat(nodes.get(2).detail().get("durationMs"))
                .isEqualTo(Duration.between(START, FINISH).toMillis());
    }

    @Test
    void pairsToolEventsWithPersistedCallsInFifoOrderAndAppendsUnmatchedCall() {
        ToolCall first = toolCall("call-1", "create_requirement",
                Map.of("title", "需求一"), Map.of("id", 11));
        ToolCall second = toolCall("call-2", "create_requirement",
                Map.of("title", "需求二"), Map.of("id", 12));
        ToolCall unmatched = toolCall("call-3", "create_prd_document",
                Map.of("title", "文档"), Map.of("id", 13));

        List<TraceNode> nodes = AgentTraceAggregator.aggregate(
                List.of(
                        event(1, "tool.completed", Map.of("name", "create_requirement", "status", "SUCCEEDED")),
                        event(2, "tool.completed", Map.of("name", "create_requirement", "status", "SUCCEEDED"))),
                List.of(), List.of(first, second, unmatched), List.of(), run(AgentRunStatus.RUNNING));

        assertThat(nodes).extracting(TraceNode::kind)
                .containsExactly(NodeKind.TOOL, NodeKind.TOOL, NodeKind.TOOL);
        assertThat(nodes.get(0).detail().get("arguments")).isEqualTo(Map.of("title", "需求一"));
        assertThat(nodes.get(0).detail().get("result")).isEqualTo(Map.of("id", 11));
        assertThat(nodes.get(1).detail().get("arguments")).isEqualTo(Map.of("title", "需求二"));
        assertThat(nodes.get(2).key()).isEqualTo("call:call-3");
        assertThat(nodes.get(2).title()).isEqualTo("create_prd_document");
        assertThat(nodes.get(2).detail().get("arguments")).isEqualTo(Map.of("title", "文档"));
    }

    @Test
    void keepsEventOnlyToolSummaryWhenCallIsNotPersisted() {
        List<TraceNode> nodes = AgentTraceAggregator.aggregate(
                List.of(event(1, "tool.failed",
                        Map.of("name", "list_requirements", "status", "FAILED",
                                "summary", "SERVER_ERROR", "errorCode", "SERVER_ERROR"))),
                List.of(), List.of(), List.of(), run(AgentRunStatus.RUNNING));

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).kind()).isEqualTo(NodeKind.TOOL);
        assertThat(nodes.get(0).status()).isEqualTo("FAILED");
        assertThat(nodes.get(0).detail().get("toolName")).isEqualTo("list_requirements");
        assertThat(nodes.get(0).detail()).doesNotContainKey("arguments");
    }

    @Test
    void enrichesApprovalNodesWithPersistedDecision() {
        Approval approval = new Approval("approval-1", "deploy_release", "HIGH", "APPROVED",
                "HIGH Tool always requires another user's approval", 42L, START.plusSeconds(600));

        List<TraceNode> nodes = AgentTraceAggregator.aggregate(
                List.of(
                        event(1, "approval.required", Map.of("approvalId", "approval-1", "status", "PENDING")),
                        event(2, "approval.approved", Map.of("approvalId", "approval-1", "status", "APPROVED"))),
                List.of(), List.of(), List.of(approval), run(AgentRunStatus.SUCCEEDED));

        assertThat(nodes).extracting(TraceNode::kind)
                .containsExactly(NodeKind.APPROVAL, NodeKind.APPROVAL);
        assertThat(nodes.get(0).title()).isEqualTo("审批 · deploy_release");
        assertThat(nodes.get(0).detail().get("status")).isEqualTo("APPROVED");
        assertThat(nodes.get(0).detail().get("riskLevel")).isEqualTo("HIGH");
        assertThat(nodes.get(0).detail().get("decidedBy")).isEqualTo(42L);
        assertThat(nodes.get(0).detail().get("expiresAt")).isEqualTo(START.plusSeconds(600));
    }

    @Test
    void supplementsStepDetailsFromPersistedSteps() {
        AgentStep step = new AgentStep(2, "TOOL", "create_requirement", "SUCCEEDED",
                "步骤输出摘要", START.plusSeconds(1), START.plusSeconds(3));

        List<TraceNode> nodes = AgentTraceAggregator.aggregate(
                List.of(event(4, "step.completed",
                        Map.of("stepNo", 2, "name", "create_requirement", "status", "SUCCEEDED"))),
                List.of(step), List.of(), List.of(), run(AgentRunStatus.RUNNING));

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).kind()).isEqualTo(NodeKind.STEP);
        assertThat(nodes.get(0).detail().get("type")).isEqualTo("TOOL");
        assertThat(nodes.get(0).detail().get("summary")).isEqualTo("步骤输出摘要");
        assertThat(nodes.get(0).detail().get("durationMs"))
                .isEqualTo(Duration.ofSeconds(2).toMillis());
    }

    @Test
    void usesMessageCompletedContentOnlyWhenNoDeltaExists() {
        List<TraceNode> withDeltas = AgentTraceAggregator.aggregate(
                List.of(
                        event(1, "message.delta", Map.of("delta", "增量")),
                        event(2, "message.delta", Map.of("delta", "正文")),
                        event(3, "message.completed", Map.of("content", "增量正文"))),
                List.of(), List.of(), List.of(), run(AgentRunStatus.SUCCEEDED));

        assertThat(withDeltas).extracting(TraceNode::kind).containsExactly(NodeKind.ANSWER);
        assertThat(withDeltas.get(0).detail().get("text")).isEqualTo("增量正文");

        List<TraceNode> completedOnly = AgentTraceAggregator.aggregate(
                List.of(event(1, "message.completed", Map.of("content", "最终回答"))),
                List.of(), List.of(), List.of(), run(AgentRunStatus.SUCCEEDED));

        assertThat(completedOnly).hasSize(1);
        assertThat(completedOnly.get(0).detail().get("text")).isEqualTo("最终回答");
    }

    private static AgentEvent event(long sequence, String type, Map<String, Object> payload) {
        return new AgentEvent(RUN_ID, sequence, type, "request-1", payload, START.plusSeconds(sequence));
    }

    private static ToolCall toolCall(String id, String name, Map<String, Object> arguments,
            Map<String, Object> result) {
        return new ToolCall(id, name, 1, "MEDIUM", "SUCCEEDED", arguments, result, null);
    }

    private static AgentRun run(AgentRunStatus status) {
        return new AgentRun(RUN_ID, 1L, null, 7L, AgentSkill.PRODUCT, MediumToolConfirmation.ALLOW,
                status, 9L, START, FINISH, null, START);
    }
}
