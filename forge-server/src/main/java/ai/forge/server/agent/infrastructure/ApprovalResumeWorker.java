package ai.forge.server.agent.infrastructure;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.AgentRunStore;
import ai.forge.server.agent.application.AgentRuntimeGateway;
import ai.forge.server.agent.infrastructure.persistence.ApprovalResumeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!test-unit")
@ConditionalOnProperty(name = "forge.infrastructure.agent.dispatch-enabled", matchIfMissing = true)
public class ApprovalResumeWorker {

    /* 恢复失败留在 Outbox，供下一轮重试。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalResumeWorker.class);
    /* 领取尚未完成的恢复事件。 */
    private final ApprovalResumeMapper mapper;
    /* 读取 Run 发起主体与固定 Skill。 */
    private final AgentRunStore runStore;
    /* 使用新凭据唤醒 forge-agent checkpoint。 */
    private final AgentRuntimeGateway runtimeGateway;
    /* 解析冻结 scope 载荷。 */
    private final ObjectMapper objectMapper;

    public ApprovalResumeWorker(ApprovalResumeMapper mapper, AgentRunStore runStore,
            AgentRuntimeGateway runtimeGateway, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.runStore = runStore;
        this.runtimeGateway = runtimeGateway;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${forge.infrastructure.agent.resume-poll-interval:1s}")
    public int dispatch() {
        int completed = 0;
        for (Map<String, Object> row : mapper.findPending(20)) {
            try {
                JsonNode payload = objectMapper.readTree(row.get("payload").toString());
                long organizationId = payload.path("organizationId").asLong();
                String runId = payload.path("runId").asText();
                var run = runStore.find(organizationId, runId).orElseThrow();
                AgentRunRequested requested = new AgentRunRequested(run.id(), organizationId,
                        run.workItemId(), run.userId(), run.skill().name(), run.mediumToolConfirmation(),
                        "resume approved checkpoint", "approval:" + payload.path("approvalId").asText());
                AgentRuntimeGateway.RunResult result = runtimeGateway.start(requested);
                if (!"SUCCEEDED".equals(result.status())) {
                    throw new IllegalStateException("Resumed Agent Run did not complete");
                }
                runStore.complete(organizationId, runId, requested.requestId(), result.answer());
                if (mapper.markProcessed(((Number) row.get("id")).longValue()) == 1) {
                    completed++;
                }
            } catch (Exception exception) {
                LOGGER.error("Agent approval resume failed: outboxId={}", row.get("id"), exception);
            }
        }
        return completed;
    }
}
