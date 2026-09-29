package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentEvent;
import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentStep;
import ai.forge.server.common.domain.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class AgentTraceService {

    /* 单批读取事件的最大条数，避免一次性加载超长结果集。 */
    private static final int EVENT_BATCH_SIZE = 200;

    /* 读取 Run 权威事实、脱敏步骤与分页事件。 */
    private final AgentRunStore runStore;

    /* 读取已持久化的工具调用事实。 */
    private final AgentToolCallStore toolCallStore;

    /* 读取已持久化的审批事实。 */
    private final ApprovalStore approvalStore;

    /* 把持久化 JSON 还原为结构化参数与结果。 */
    private final ObjectMapper objectMapper;

    public AgentTraceService(AgentRunStore runStore, AgentToolCallStore toolCallStore,
            ApprovalStore approvalStore, ObjectMapper objectMapper) {
        this.runStore = runStore;
        this.toolCallStore = toolCallStore;
        this.approvalStore = approvalStore;
        this.objectMapper = objectMapper;
    }

    /* 解释 Run 并聚合轨迹；scope 为 organization 与 Run 发起人，不要求 RBAC 权限码。 */
    public AgentRunTrace explain(long userId, long organizationId, String runId) {
        AgentRun run = runStore.find(organizationId, runId).orElseThrow(ResourceNotFoundException::new);
        if (run.userId() != userId) {
            throw new ResourceNotFoundException();
        }
        List<AgentEvent> events = readAllEvents(organizationId, runId);
        List<AgentStep> steps = runStore.findSteps(organizationId, runId);
        List<AgentTraceAggregator.ToolCall> toolCalls = toolCallStore.findByRun(organizationId, runId).stream()
                .map(this::toolCall)
                .toList();
        List<AgentTraceAggregator.Approval> approvals =
                approvalStore.findByRunAscending(organizationId, runId).stream()
                        .map(this::approval)
                        .toList();
        List<TraceNode> nodes = AgentTraceAggregator.aggregate(events, steps, toolCalls, approvals, run);
        return new AgentRunTrace(run.id(), run.skill(), run.workItemId(), run.status(), run.terminal(),
                run.startedAt(), run.finishedAt(), run.errorCode(), run.lastSequence(), nodes);
    }

    private List<AgentEvent> readAllEvents(long organizationId, String runId) {
        List<AgentEvent> events = new ArrayList<>();
        long cursor = 0;
        while (true) {
            List<AgentEvent> batch = runStore.findEventsAfter(organizationId, runId, cursor, EVENT_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            events.addAll(batch);
            cursor = batch.get(batch.size() - 1).sequence();
            if (batch.size() < EVENT_BATCH_SIZE) {
                break;
            }
        }
        return events;
    }

    private AgentTraceAggregator.ToolCall toolCall(AgentToolCallStore.PersistedCall call) {
        return new AgentTraceAggregator.ToolCall(call.toolCallId(), call.toolName(), call.toolVersion(),
                call.riskLevel(), call.status(), jsonMap(call.argumentsJson()), jsonMap(call.resultJson()),
                call.approvalId());
    }

    private AgentTraceAggregator.Approval approval(Map<String, Object> row) {
        return new AgentTraceAggregator.Approval(text(row.get("id")), text(row.get("tool_name")),
                text(row.get("risk_level")), text(row.get("status")), text(row.get("reason")),
                row.get("approver_user_id") == null
                        ? null : ((Number) row.get("approver_user_id")).longValue(),
                instant(row.get("expires_at")));
    }

    private Map<String, Object> jsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted tool call JSON is invalid", exception);
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Instant instant(Object value) {
        return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }
}
