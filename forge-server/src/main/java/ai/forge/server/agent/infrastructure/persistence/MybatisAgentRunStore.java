package ai.forge.server.agent.infrastructure.persistence;

import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.agent.domain.AgentEvent;
import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentRunIdempotencyConflictException;
import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.AgentStep;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisAgentRunStore implements AgentRunStore {

    /* 事件 JSON 转换成框架无关 Map 时使用的稳定泛型类型。 */
    private static final TypeReference<Map<String, Object>> EVENT_PAYLOAD_TYPE = new TypeReference<>() {
    };

    /* 执行显式 scope、行锁和追加事件 SQL。 */
    private final AgentRunMapper mapper;

    /* 在持久 JSON 与事件信封之间转换。 */
    private final ObjectMapper objectMapper;

    public MybatisAgentRunStore(AgentRunMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public CreateResult create(
            String runId,
            long workspaceId,
            long projectId,
            Long workItemId,
            long userId,
            AgentSkill skill,
            MediumToolConfirmation mediumToolConfirmation,
            String messageRedacted,
            String clientRequestId,
            String requestHash,
            String requestId) {
        Optional<Map<String, Object>> existing = findByClientRequest(workspaceId, projectId, userId, clientRequestId);
        if (existing.isPresent()) {
            return repeated(existing.orElseThrow(), requestHash);
        }
        try {
            mapper.insertRun(
                    runId,
                    workspaceId,
                    projectId,
                    workItemId,
                    userId,
                    skill.name(),
                    mediumToolConfirmation.name(),
                    messageRedacted,
                    clientRequestId,
                    requestHash);
            mapper.insertEvent(
                    workspaceId,
                    projectId,
                    runId,
                    1,
                    "agent.queued",
                    requestId,
                    json(Map.of("status", "QUEUED")));
            return new CreateResult(find(workspaceId, projectId, runId).orElseThrow(), true);
        } catch (DuplicateKeyException exception) {
            Map<String, Object> repeated = findByClientRequest(workspaceId, projectId, userId, clientRequestId)
                    .orElseThrow(() -> exception);
            return repeated(repeated, requestHash);
        }
    }

    @Override
    public Optional<AgentRun> find(long workspaceId, long projectId, String runId) {
        return mapper.findRun(workspaceId, projectId, runId).stream().findFirst().map(this::run);
    }

    @Override
    public List<AgentStep> findSteps(long workspaceId, long projectId, String runId) {
        return mapper.findSteps(workspaceId, projectId, runId).stream().map(this::step).toList();
    }

    @Override
    public List<AgentEvent> findEventsAfter(
            long workspaceId, long projectId, String runId, long afterSequence, int limit) {
        return mapper.findEventsAfter(workspaceId, projectId, runId, afterSequence, limit).stream()
                .map(this::event)
                .toList();
    }

    @Override
    @Transactional
    public void start(long workspaceId, long projectId, String runId, String requestId) {
        Map<String, Object> locked = mapper.lockRun(workspaceId, projectId, runId).stream()
                .findFirst()
                .orElse(null);
        if (locked == null || !"QUEUED".equals(text(locked, "status"))) {
            return;
        }
        long firstSequence = number(locked, "last_sequence") + 1;
        if (mapper.markRunning(workspaceId, projectId, runId, firstSequence + 1) != 1) {
            return;
        }
        mapper.insertAgentStep(workspaceId, projectId, runId);
        mapper.insertEvent(
                workspaceId,
                projectId,
                runId,
                firstSequence,
                "agent.started",
                requestId,
                json(Map.of("status", "RUNNING")));
        mapper.insertEvent(
                workspaceId,
                projectId,
                runId,
                firstSequence + 1,
                "step.started",
                requestId,
                json(Map.of("stepNo", 1, "name", "Create plan", "status", "RUNNING")));
    }

    @Override
    @Transactional
    public void complete(
            long workspaceId,
            long projectId,
            String runId,
            String requestId,
            String summary) {
        Map<String, Object> locked = mapper.lockRun(workspaceId, projectId, runId).stream()
                .findFirst()
                .orElse(null);
        if (locked == null || !"RUNNING".equals(text(locked, "status"))) {
            return;
        }
        long firstSequence = number(locked, "last_sequence") + 1;
        if (mapper.completeAgentStep(workspaceId, projectId, runId, summary) != 1
                || mapper.markSucceeded(workspaceId, projectId, runId, firstSequence + 1) != 1) {
            throw new IllegalStateException("Fake run state changed unexpectedly");
        }
        mapper.insertEvent(
                workspaceId,
                projectId,
                runId,
                firstSequence,
                "step.completed",
                requestId,
                json(Map.of(
                        "stepNo", 1,
                        "name", "Create plan",
                        "status", "SUCCEEDED",
                        "summary", summary)));
        mapper.insertEvent(
                workspaceId,
                projectId,
                runId,
                firstSequence + 1,
                "agent.completed",
                requestId,
                json(Map.of("status", "SUCCEEDED", "summary", summary)));
    }

    @Override
    @Transactional
    public void fail(
            long workspaceId, long projectId, String runId, String requestId, String errorCode) {
        Map<String, Object> locked = mapper.lockRun(workspaceId, projectId, runId).stream()
                .findFirst()
                .orElse(null);
        if (locked == null || AgentRunStatus.valueOf(text(locked, "status")).terminal()) {
            return;
        }
        long sequence = number(locked, "last_sequence") + 1;
        if (mapper.markFailed(workspaceId, projectId, runId, sequence, errorCode) == 1) {
            mapper.insertEvent(
                    workspaceId,
                    projectId,
                    runId,
                    sequence,
                    "agent.failed",
                    requestId,
                    json(Map.of("status", "FAILED", "errorCode", errorCode)));
        }
    }

    private Optional<Map<String, Object>> findByClientRequest(
            long workspaceId, long projectId, long userId, String clientRequestId) {
        return mapper.findByClientRequest(workspaceId, projectId, userId, clientRequestId).stream().findFirst();
    }

    private CreateResult repeated(Map<String, Object> row, String requestHash) {
        if (!requestHash.equals(text(row, "request_hash"))) {
            throw new AgentRunIdempotencyConflictException();
        }
        return new CreateResult(run(row), false);
    }

    private AgentRun run(Map<String, Object> row) {
        return new AgentRun(
                text(row, "id"),
                number(row, "workspace_id"),
                number(row, "project_id"),
                nullableNumber(row, "work_item_id"),
                number(row, "user_id"),
                AgentSkill.valueOf(text(row, "skill")),
                MediumToolConfirmation.valueOf(text(row, "medium_tool_confirmation")),
                AgentRunStatus.valueOf(text(row, "status")),
                number(row, "last_sequence"),
                instant(row.get("started_at")),
                instant(row.get("finished_at")),
                nullableText(row, "error_code"),
                instant(row.get("created_at")));
    }

    private AgentStep step(Map<String, Object> row) {
        return new AgentStep(
                ((Number) row.get("step_no")).intValue(),
                text(row, "type"),
                text(row, "name"),
                text(row, "status"),
                nullableText(row, "output_summary"),
                instant(row.get("started_at")),
                instant(row.get("finished_at")));
    }

    private AgentEvent event(Map<String, Object> row) {
        try {
            return new AgentEvent(
                    text(row, "run_id"),
                    number(row, "sequence"),
                    text(row, "event_type"),
                    text(row, "request_id"),
                    objectMapper.readValue(text(row, "payload_json"), EVENT_PAYLOAD_TYPE),
                    instant(row.get("created_at")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted Agent event payload is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent event payload cannot be serialized", exception);
        }
    }

    private Instant instant(Object value) {
        return value == null ? null : ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Long nullableNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
