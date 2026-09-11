package ai.forge.server.agent.infrastructure.persistence;

import ai.forge.server.agent.application.AgentToolCallStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisAgentToolCallStore implements AgentToolCallStore {

    /* 执行带 scope 条件与幂等键唯一约束的 Tool Call SQL。 */
    private final AgentToolCallMapper mapper;

    public MybatisAgentToolCallStore(AgentToolCallMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<StoredToolCall> findSuccessful(
            long organizationId, String runId, String idempotencyKey) {
        List<Map<String, Object>> rows =
                mapper.findSuccessful(organizationId, runId, idempotencyKey);
        return rows.stream().findFirst().map(row -> new StoredToolCall(
                row.get("tool_name").toString(),
                ((Number) row.get("tool_version")).intValue(),
                row.get("arguments_json").toString(),
                row.get("result_json").toString()));
    }

    @Override
    public void record(
            long organizationId,
            String runId,
            String toolCallId,
            String toolName,
            int toolVersion,
            String riskLevel,
            String argumentsJson,
            String resultJson,
            String idempotencyKey) {
        mapper.insertToolCall(
                runId,
                organizationId,
                toolCallId,
                toolName,
                toolVersion,
                riskLevel,
                argumentsJson,
                resultJson,
                idempotencyKey);
    }
}
