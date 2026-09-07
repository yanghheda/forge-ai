package ai.forge.server.release.infrastructure.persistence;

import ai.forge.server.release.application.DeploymentStore;
import ai.forge.server.release.application.DeploymentView;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisDeploymentStore implements DeploymentStore {

    /* 所有部署 SQL 均显式携带租户与项目范围。 */
    private final DeploymentMapper mapper;

    public MybatisDeploymentStore(DeploymentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DeploymentView request(long workspaceId, long projectId, long releaseId, long requestedBy,
            long releaseVersion, long precheckId, String argumentHash, boolean simulateFailure,
            String idempotencyKey, Instant expiresAt, String approvalSource, String agentApprovalId) {
        Map<String, Object> generated = new LinkedHashMap<>();
        String status = "AGENT_TOOL".equals(approvalSource) ? "APPROVED" : "PENDING_APPROVAL";
        try {
            mapper.insert(generated, workspaceId, projectId, releaseId, status, requestedBy,
                    LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC), releaseVersion, precheckId,
                    argumentHash, simulateFailure, idempotencyKey, approvalSource, agentApprovalId);
        } catch (DuplicateKeyException exception) {
            return mapper.findByIdempotencyKey(workspaceId, projectId, idempotencyKey).stream()
                    .findFirst().map(this::view).orElseThrow(() -> exception);
        }
        return find(workspaceId, projectId, number(generated.get("id"))).orElseThrow();
    }

    @Override
    public Optional<DeploymentView> find(long workspaceId, long projectId, long deploymentId) {
        return mapper.find(workspaceId, projectId, deploymentId).stream().findFirst().map(this::view);
    }

    @Override
    public List<DeploymentView> list(long workspaceId, long projectId, long releaseId) {
        return mapper.list(workspaceId, projectId, releaseId).stream().map(this::view).toList();
    }

    @Override
    public Optional<DeploymentView> findByIdempotencyKey(long workspaceId, long projectId, String idempotencyKey) {
        return mapper.findByIdempotencyKey(workspaceId, projectId, idempotencyKey).stream()
                .findFirst().map(this::view);
    }

    @Override
    public List<DeploymentView> approved() {
        return mapper.findApproved().stream().map(this::view).toList();
    }

    @Override
    public boolean decide(long workspaceId, long projectId, long deploymentId, long approverUserId,
            String status, long expectedVersion) {
        return mapper.decide(workspaceId, projectId, deploymentId, approverUserId, status, expectedVersion) == 1;
    }

    @Override
    public boolean expire(long workspaceId, long projectId, long deploymentId, long expectedVersion) {
        return mapper.expire(workspaceId, projectId, deploymentId, expectedVersion) == 1;
    }

    @Override
    public boolean claim(long deploymentId, long expectedVersion) {
        return mapper.claim(deploymentId, expectedVersion) == 1;
    }

    @Override
    public void complete(long deploymentId, boolean succeeded, String resultCode, String resultSummary) {
        if (mapper.complete(deploymentId, succeeded ? "SUCCEEDED" : "FAILED", resultCode, resultSummary) != 1) {
            throw new IllegalStateException("Deployment is not in DEPLOYING state");
        }
    }

    @Override
    public void updateReleaseStatus(long workspaceId, long projectId, long releaseId, String status) {
        if (mapper.updateReleaseStatus(workspaceId, projectId, releaseId, status) != 1) {
            throw new IllegalStateException("Release status cannot be updated");
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void completeRequirements(long workspaceId, long projectId, long releaseId, long actorId,
            long deploymentId) {
        mapper.insertRequirementEvents(workspaceId, projectId, releaseId, actorId, deploymentId,
                "MARK_RELEASED", "READY_FOR_RELEASE", "RELEASED");
        mapper.transitionRequirements(workspaceId, projectId, releaseId, "READY_FOR_RELEASE", "RELEASED");
        mapper.insertRequirementEvents(workspaceId, projectId, releaseId, actorId, deploymentId,
                "CLOSE_REQUIREMENT", "RELEASED", "DONE");
        mapper.transitionRequirements(workspaceId, projectId, releaseId, "RELEASED", "DONE");
    }

    @Override
    public void audit(long workspaceId, long projectId, String actorType, long actorId, String action,
            long deploymentId, String result, String requestId, String runId) {
        mapper.audit(workspaceId, projectId, actorType, actorId, action, deploymentId, result, requestId, runId);
    }

    private DeploymentView view(Map<String, Object> row) {
        return new DeploymentView(number(row.get("id")), number(row.get("workspace_id")),
                number(row.get("project_id")), number(row.get("release_id")), text(row.get("mode")),
                text(row.get("status")), number(row.get("requested_by")), nullableNumber(row.get("approver_user_id")),
                instant(row.get("approval_expires_at")), number(row.get("release_version")),
                number(row.get("precheck_id")), text(row.get("argument_hash")),
                booleanValue(row.get("simulate_failure")), textOrNull(row.get("result_code")),
                textOrNull(row.get("result_summary")),
                text(row.get("approval_source")),
                textOrNull(row.get("agent_approval_id")), number(row.get("version")), instant(row.get("created_at")),
                nullableInstant(row.get("started_at")), nullableInstant(row.get("finished_at")));
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static String text(Object value) {
        return value.toString();
    }

    private static String textOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : ((Number) value).intValue() != 0;
    }

    private static Instant instant(Object value) {
        return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }

    private static Instant nullableInstant(Object value) {
        return value == null ? null : instant(value);
    }
}
