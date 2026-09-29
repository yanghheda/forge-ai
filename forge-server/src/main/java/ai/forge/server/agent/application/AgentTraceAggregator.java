package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentEvent;
import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentStep;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 纯聚合器：把持久化事件、步骤、工具调用与审批事实整理为有序轨迹节点。
 *
 * <p>该类不依赖 Spring 或 MyBatis，输入输出均为普通数据，便于独立演进与单测覆盖。</p>
 */
public final class AgentTraceAggregator {

    /* 事件类型到轨迹节点 kind 的显式映射表；新增事件类型在此登记，未登记类型统一兜底为 EVENT。 */
    private static final Map<String, NodeKind> EVENT_KINDS = Map.ofEntries(
            Map.entry("thinking.delta", NodeKind.THINKING),
            Map.entry("reasoning.delta", NodeKind.REASONING),
            Map.entry("plan.created", NodeKind.PLAN),
            Map.entry("step.started", NodeKind.STEP),
            Map.entry("step.completed", NodeKind.STEP),
            Map.entry("step.failed", NodeKind.STEP),
            Map.entry("tool.requested", NodeKind.TOOL),
            Map.entry("tool.started", NodeKind.TOOL),
            Map.entry("tool.completed", NodeKind.TOOL),
            Map.entry("tool.failed", NodeKind.TOOL),
            Map.entry("approval.required", NodeKind.APPROVAL),
            Map.entry("approval.approved", NodeKind.APPROVAL),
            Map.entry("approval.rejected", NodeKind.APPROVAL),
            Map.entry("approval.expired", NodeKind.APPROVAL),
            Map.entry("approval.cancelled", NodeKind.APPROVAL),
            Map.entry("message.delta", NodeKind.ANSWER),
            Map.entry("message.completed", NodeKind.ANSWER),
            Map.entry("agent.completed", NodeKind.TERMINAL),
            Map.entry("agent.failed", NodeKind.TERMINAL),
            Map.entry("agent.cancelled", NodeKind.TERMINAL));

    /* 模型思考来源图节点到展示文案的映射；未登记节点直接展示原始标识。 */
    private static final Map<String, String> THINKING_NODE_LABELS = Map.of(
            "create_plan", "计划",
            "select_tool", "选择工具",
            "finalize", "收尾");

    private AgentTraceAggregator() {
    }

    /**
     * 聚合为按事件序号升序的轨迹节点；已持久化但无事件配对的工具调用追加到末尾。
     */
    public static List<TraceNode> aggregate(
            List<AgentEvent> events,
            List<AgentStep> steps,
            List<ToolCall> toolCalls,
            List<Approval> approvals,
            AgentRun run) {
        List<AgentEvent> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparingLong(AgentEvent::sequence));

        Map<Integer, AgentStep> stepsByNumber = new LinkedHashMap<>();
        for (AgentStep step : steps) {
            stepsByNumber.putIfAbsent(step.stepNo(), step);
        }
        Map<String, Approval> approvalsById = new LinkedHashMap<>();
        for (Approval approval : approvals) {
            approvalsById.putIfAbsent(approval.approvalId(), approval);
        }

        List<TraceNode> nodes = new ArrayList<>();
        boolean[] consumedCalls = new boolean[toolCalls.size()];
        /* callId 到既有 TOOL 节点下标的反查表；同一调用的 started 与终态事件据此合并为单个节点。 */
        Map<String, Integer> toolNodeIndexByCallId = new LinkedHashMap<>();
        boolean hasMessageDelta = ordered.stream().anyMatch(event -> "message.delta".equals(event.type()));

        int index = 0;
        while (index < ordered.size()) {
            AgentEvent event = ordered.get(index);
            switch (kindOf(event.type())) {
                case THINKING -> index += mergeThinking(ordered, index, nodes);
                case REASONING -> index += mergeReasoning(ordered, index, nodes);
                case ANSWER -> index += appendAnswer(ordered, index, hasMessageDelta, nodes);
                case PLAN -> {
                    appendPlan(event, nodes);
                    index++;
                }
                case STEP -> {
                    appendStep(event, stepsByNumber.get(stepNumber(event)), nodes);
                    index++;
                }
                case TOOL -> {
                    appendTool(event, toolCalls, consumedCalls, toolNodeIndexByCallId, nodes);
                    index++;
                }
                case APPROVAL -> {
                    appendApproval(event, approvalsById, nodes);
                    index++;
                }
                case TERMINAL -> {
                    appendTerminal(event, run, nodes);
                    index++;
                }
                case EVENT -> {
                    appendEvent(event, nodes);
                    index++;
                }
            }
        }

        long synthetic = ordered.isEmpty() ? 0L : ordered.get(ordered.size() - 1).sequence();
        for (int callIndex = 0; callIndex < toolCalls.size(); callIndex++) {
            if (consumedCalls[callIndex]) {
                continue;
            }
            ToolCall call = toolCalls.get(callIndex);
            synthetic++;
            nodes.add(new TraceNode("call:" + call.toolCallId(), NodeKind.TOOL, call.toolName(), call.status(),
                    synthetic, run == null ? null : run.startedAt(), run == null ? null : run.finishedAt(),
                    persistedToolDetail(call, Map.of())));
        }
        return nodes;
    }

    private static int mergeThinking(List<AgentEvent> ordered, int start, List<TraceNode> nodes) {
        AgentEvent first = ordered.get(start);
        String node = text(first.payload().get("node"));
        StringBuilder merged = new StringBuilder();
        long expected = first.sequence();
        int cursor = start;
        while (cursor < ordered.size()) {
            AgentEvent candidate = ordered.get(cursor);
            if (!"thinking.delta".equals(candidate.type())
                    || !Objects.equals(text(candidate.payload().get("node")), node)
                    || candidate.sequence() != expected) {
                break;
            }
            merged.append(delta(candidate));
            expected++;
            cursor++;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("text", merged.toString());
        detail.put("node", node);
        nodes.add(new TraceNode("seq:" + first.sequence(), NodeKind.THINKING, thinkingTitle(node), null,
                first.sequence(), first.timestamp(), ordered.get(cursor - 1).timestamp(), detail));
        return cursor - start;
    }

    private static int mergeReasoning(List<AgentEvent> ordered, int start, List<TraceNode> nodes) {
        AgentEvent first = ordered.get(start);
        StringBuilder merged = new StringBuilder();
        long expected = first.sequence();
        int cursor = start;
        while (cursor < ordered.size()) {
            AgentEvent candidate = ordered.get(cursor);
            if (!"reasoning.delta".equals(candidate.type()) || candidate.sequence() != expected) {
                break;
            }
            merged.append(delta(candidate));
            expected++;
            cursor++;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("text", merged.toString());
        nodes.add(new TraceNode("seq:" + first.sequence(), NodeKind.REASONING, "执行思路", null,
                first.sequence(), first.timestamp(), ordered.get(cursor - 1).timestamp(), detail));
        return cursor - start;
    }

    private static int appendAnswer(
            List<AgentEvent> ordered, int start, boolean hasMessageDelta, List<TraceNode> nodes) {
        AgentEvent first = ordered.get(start);
        if ("message.completed".equals(first.type())) {
            /* 已有增量正文时不再重复展示同一回答；仅当只有完成事件时才用其 content 生成节点。 */
            if (!hasMessageDelta) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("text", text(first.payload().get("content")));
                nodes.add(new TraceNode("seq:" + first.sequence(), NodeKind.ANSWER, "回答", null,
                        first.sequence(), first.timestamp(), first.timestamp(), detail));
            }
            return 1;
        }
        StringBuilder merged = new StringBuilder();
        long expected = first.sequence();
        int cursor = start;
        while (cursor < ordered.size()) {
            AgentEvent candidate = ordered.get(cursor);
            if (!"message.delta".equals(candidate.type()) || candidate.sequence() != expected) {
                break;
            }
            merged.append(delta(candidate));
            expected++;
            cursor++;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("text", merged.toString());
        nodes.add(new TraceNode("seq:" + first.sequence(), NodeKind.ANSWER, "回答", null,
                first.sequence(), first.timestamp(), ordered.get(cursor - 1).timestamp(), detail));
        return cursor - start;
    }

    private static void appendPlan(AgentEvent event, List<TraceNode> nodes) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("steps", event.payload().get("steps"));
        nodes.add(new TraceNode("seq:" + event.sequence(), NodeKind.PLAN, "计划", null,
                event.sequence(), event.timestamp(), event.timestamp(), detail));
    }

    private static void appendStep(AgentEvent event, AgentStep step, List<TraceNode> nodes) {
        Map<String, Object> payload = event.payload();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stepNo", payload.get("stepNo"));
        detail.put("type", step == null ? null : step.type());
        detail.put("name", firstNonNull(payload.get("name"), step == null ? null : step.name()));
        detail.put("status", firstNonNull(step == null ? null : step.status(), payload.get("status")));
        detail.put("summary", firstNonNull(step == null ? null : step.outputSummary(), payload.get("summary")));
        detail.put("errorCode", payload.get("errorCode"));
        detail.put("durationMs", step == null ? null : durationMs(step.startedAt(), step.finishedAt()));
        String name = text(detail.get("name"));
        nodes.add(new TraceNode("seq:" + event.sequence(), NodeKind.STEP, name == null ? "步骤" : name,
                text(detail.get("status")), event.sequence(),
                step == null || step.startedAt() == null ? event.timestamp() : step.startedAt(),
                step == null ? event.timestamp() : step.finishedAt(), detail));
    }

    private static void appendTool(
            AgentEvent event,
            List<ToolCall> toolCalls,
            boolean[] consumedCalls,
            Map<String, Integer> toolNodeIndexByCallId,
            List<TraceNode> nodes) {
        Map<String, Object> payload = event.payload();
        String callId = text(payload.get("callId"));
        String toolName = text(payload.get("name"));
        boolean isFinal = "tool.completed".equals(event.type()) || "tool.failed".equals(event.type());

        int match = -1;
        if (callId != null) {
            for (int candidate = 0; candidate < toolCalls.size(); candidate++) {
                if (!consumedCalls[candidate] && Objects.equals(toolCalls.get(candidate).toolCallId(), callId)) {
                    match = candidate;
                    break;
                }
            }
        }
        /* callId 缺失或未命中持久化事实时，退回按工具名 FIFO 配对，保持旧行为兼容。 */
        if (match < 0) {
            for (int candidate = 0; candidate < toolCalls.size(); candidate++) {
                if (!consumedCalls[candidate] && Objects.equals(toolCalls.get(candidate).toolName(), toolName)) {
                    match = candidate;
                    break;
                }
            }
        }
        ToolCall call = null;
        if (match >= 0) {
            consumedCalls[match] = true;
            call = toolCalls.get(match);
        }

        Integer existingIndex = callId == null ? null : toolNodeIndexByCallId.get(callId);
        if (existingIndex != null) {
            /* 同一 callId 的后续事件（终态或审批恢复后的重复 started）就地更新既有节点，避免重复工具卡片。 */
            TraceNode existing = nodes.get(existingIndex);
            Map<String, Object> detail = call != null
                    ? persistedToolDetail(call, payload)
                    : overlayToolDetail(existing.detail(), payload);
            String status = call != null
                    ? (call.status() == null ? text(payload.get("status")) : call.status())
                    : text(payload.get("status"));
            String title = toolName != null ? toolName : existing.title();
            nodes.set(existingIndex, new TraceNode(existing.key(), NodeKind.TOOL, title, status,
                    existing.sequence(), existing.startedAt(),
                    isFinal ? event.timestamp() : existing.finishedAt(), detail));
            return;
        }

        Map<String, Object> detail;
        String status;
        if (call != null) {
            detail = persistedToolDetail(call, payload);
            status = call.status() == null ? text(payload.get("status")) : call.status();
        } else {
            detail = eventToolDetail(payload);
            status = text(payload.get("status"));
        }
        nodes.add(new TraceNode("seq:" + event.sequence(), NodeKind.TOOL,
                toolName == null ? "工具调用" : toolName, status, event.sequence(),
                event.timestamp(), isFinal ? event.timestamp() : null, detail));
        if (callId != null) {
            toolNodeIndexByCallId.put(callId, nodes.size() - 1);
        }
    }

    private static void appendApproval(
            AgentEvent event, Map<String, Approval> approvalsById, List<TraceNode> nodes) {
        String approvalId = text(event.payload().get("approvalId"));
        Approval approval = approvalsById.get(approvalId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("approvalId", approvalId);
        detail.put("toolName", approval == null ? text(event.payload().get("toolName")) : approval.toolName());
        detail.put("riskLevel", approval == null ? null : approval.riskLevel());
        detail.put("status", approval == null ? text(event.payload().get("status")) : approval.status());
        detail.put("reason", approval == null ? null : approval.reason());
        detail.put("decidedBy", approval == null ? null : approval.decidedBy());
        detail.put("expiresAt", approval == null ? null : approval.expiresAt());
        String toolName = text(detail.get("toolName"));
        nodes.add(new TraceNode("seq:" + event.sequence(), NodeKind.APPROVAL,
                toolName == null ? "审批决策" : "审批 · " + toolName, text(detail.get("status")),
                event.sequence(), event.timestamp(), event.timestamp(), detail));
    }

    private static void appendTerminal(AgentEvent event, AgentRun run, List<TraceNode> nodes) {
        Map<String, Object> payload = event.payload();
        String status = text(firstNonNull(payload.get("status"), run == null ? null : run.status().name()));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", status);
        detail.put("summary", payload.get("summary"));
        detail.put("errorCode", firstNonNull(payload.get("errorCode"), run == null ? null : run.errorCode()));
        detail.put("durationMs", run == null ? null : durationMs(run.startedAt(), run.finishedAt()));
        nodes.add(new TraceNode("seq:" + event.sequence(), NodeKind.TERMINAL, terminalTitle(status), status,
                event.sequence(), event.timestamp(), event.timestamp(), detail));
    }

    private static void appendEvent(AgentEvent event, List<TraceNode> nodes) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("eventType", event.type());
        detail.put("payload", event.payload());
        nodes.add(new TraceNode("seq:" + event.sequence(), NodeKind.EVENT, event.type(), null,
                event.sequence(), event.timestamp(), event.timestamp(), detail));
    }

    private static Map<String, Object> persistedToolDetail(ToolCall call, Map<String, Object> payload) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("toolName", call.toolName());
        detail.put("toolVersion", call.toolVersion());
        detail.put("riskLevel", call.riskLevel());
        detail.put("status", call.status() == null ? text(payload.get("status")) : call.status());
        if (payload.containsKey("summary")) {
            detail.put("summary", payload.get("summary"));
        }
        if (payload.containsKey("errorCode")) {
            detail.put("errorCode", payload.get("errorCode"));
        }
        detail.put("arguments", call.arguments());
        detail.put("result", call.result());
        detail.put("approvalId", call.approvalId());
        return detail;
    }

    private static Map<String, Object> eventToolDetail(Map<String, Object> payload) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("toolName", text(payload.get("name")));
        detail.put("status", payload.get("status"));
        detail.put("summary", payload.get("summary"));
        detail.put("errorCode", payload.get("errorCode"));
        return detail;
    }

    /* 无持久化事实配对时就地更新节点：保留既有 detail（尤其 toolName），再以 payload 覆盖 status/summary/errorCode。 */
    private static Map<String, Object> overlayToolDetail(Map<String, Object> existing, Map<String, Object> payload) {
        Map<String, Object> detail = new LinkedHashMap<>(existing);
        if (payload.containsKey("status")) {
            detail.put("status", payload.get("status"));
        }
        if (payload.containsKey("summary")) {
            detail.put("summary", payload.get("summary"));
        }
        if (payload.containsKey("errorCode")) {
            detail.put("errorCode", payload.get("errorCode"));
        }
        return detail;
    }

    private static NodeKind kindOf(String eventType) {
        NodeKind mapped = EVENT_KINDS.get(eventType);
        if (mapped != null) {
            return mapped;
        }
        if (eventType.startsWith("step.")) {
            return NodeKind.STEP;
        }
        if (eventType.startsWith("tool.")) {
            return NodeKind.TOOL;
        }
        if (eventType.startsWith("approval.")) {
            return NodeKind.APPROVAL;
        }
        return NodeKind.EVENT;
    }

    private static Integer stepNumber(AgentEvent event) {
        Object value = event.payload().get("stepNo");
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String delta(AgentEvent event) {
        return text(event.payload().get("delta"));
    }

    private static String thinkingTitle(String node) {
        if (node == null) {
            return "模型思考";
        }
        return "模型思考 · " + THINKING_NODE_LABELS.getOrDefault(node, node);
    }

    private static String terminalTitle(String status) {
        if ("SUCCEEDED".equals(status)) {
            return "运行完成";
        }
        if ("FAILED".equals(status)) {
            return "运行失败";
        }
        if ("CANCELLED".equals(status)) {
            return "运行取消";
        }
        return "运行结束";
    }

    private static Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /* 轨迹节点类型；前端按此值分派渲染，未知值走通用渲染。 */
    public enum NodeKind {
        /* 模型思考内容（Chain-of-Thought）聚合节点。 */
        THINKING,
        /* 计划摘要执行思路聚合节点。 */
        REASONING,
        /* 计划创建节点。 */
        PLAN,
        /* 执行步骤节点。 */
        STEP,
        /* 工具调用节点。 */
        TOOL,
        /* 人工审批节点。 */
        APPROVAL,
        /* Agent 最终回答节点。 */
        ANSWER,
        /* Run 终态节点。 */
        TERMINAL,
        /* 未识别事件类型的兜底节点。 */
        EVENT
    }

    /* 已持久化的工具调用事实，用于补充轨迹的结构化参数与结果。 */
    public record ToolCall(
            /* Agent 侧生成的本次调用标识，用作独立节点的稳定键。 */
            String toolCallId,
            /* Tool 契约名称，用于与事件按 FIFO 配对。 */
            String toolName,
            /* 契约版本。 */
            int toolVersion,
            /* 风险等级。 */
            String riskLevel,
            /* 调用状态。 */
            String status,
            /* 结构化输入参数。 */
            Map<String, Object> arguments,
            /* 结构化结果；未完成时为空。 */
            Map<String, Object> result,
            /* 关联审批标识；无需审批时为空。 */
            String approvalId) {
    }

    /* 已持久化的审批事实，用于补充轨迹的决策结果。 */
    public record Approval(
            /* 审批标识。 */
            String approvalId,
            /* 冻结的 Tool 名称。 */
            String toolName,
            /* 风险等级。 */
            String riskLevel,
            /* 审批状态。 */
            String status,
            /* 向审批人解释风险的脱敏原因。 */
            String reason,
            /* 作出决定的用户标识；未决定时为空。 */
            Long decidedBy,
            /* 审批最晚可决策时间。 */
            Instant expiresAt) {
    }
}
