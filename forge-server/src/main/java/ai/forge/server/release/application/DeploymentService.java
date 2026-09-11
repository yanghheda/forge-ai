package ai.forge.server.release.application;

import ai.forge.server.agent.domain.ToolExecutionRejectedException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class DeploymentService {

    /* 权限与项目范围的最终裁决器。 */
    private final PermissionEvaluator permissions;
    /* Release 与 Precheck 的业务事实来源。 */
    private final ReleaseStore releases;
    /* 部署审批、执行与审计事实端口。 */
    private final DeploymentStore deployments;

    public DeploymentService(PermissionEvaluator permissions, ReleaseStore releases, DeploymentStore deployments) {
        this.permissions = permissions;
        this.releases = releases;
        this.deployments = deployments;
    }

    @Transactional
    public DeploymentView request(long userId, long organizationId, long releaseId,
            boolean simulateFailure, String idempotencyKey, String requestId) {
        permissions.requireOrganization(userId, organizationId, "release.deploy");
        return create(organizationId, releaseId, userId, simulateFailure, idempotencyKey,
                "HUMAN_REQUEST", null, requestId);
    }

    @Transactional
    public DeploymentView requestFromApprovedAgent(long userId, long organizationId, long releaseId,
            boolean simulateFailure, String idempotencyKey, String approvalId, String runId) {
        permissions.requireOrganization(userId, organizationId, "release.deploy");
        return create(organizationId, releaseId, userId, simulateFailure, idempotencyKey,
                "AGENT_TOOL", approvalId, runId);
    }

    public List<DeploymentView> list(long userId, long organizationId, long releaseId) {
        permissions.requireOrganization(userId, organizationId, "release.read");
        releases.find(organizationId, releaseId).orElseThrow(ResourceNotFoundException::new);
        return deployments.list(organizationId, releaseId);
    }

    @Transactional
    public DeploymentView decide(long userId, long organizationId, long deploymentId,
            boolean approve, long expectedVersion, String requestId) {
        permissions.requireOrganization(userId, organizationId, "approval.decide");
        DeploymentView current = find(organizationId, deploymentId);
        if (current.requestedBy() == userId) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(), "SELF_APPROVAL_FORBIDDEN",
                    "Requester cannot approve their own HIGH deployment");
        }
        if (!current.approvalExpiresAt().isAfter(Instant.now())) {
            if (!deployments.expire(organizationId, deploymentId, expectedVersion)) {
                throw new VersionConflictException();
            }
            deployments.audit(organizationId, "USER", userId, "deployment.approval.expired",
                    deploymentId, "EXPIRED", requestId, null);
            return find(organizationId, deploymentId);
        }
        if (approve) {
            ReleaseView release = requireDeployable(organizationId, current.releaseId());
            if (release.version() != current.releaseVersion()
                    || release.latestPrecheck().id() != current.precheckId()) {
                throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "RESOURCE_VERSION_CHANGED",
                        "Release or Precheck changed after deployment approval request");
            }
        }
        String status = approve ? "APPROVED" : "REJECTED";
        if (!deployments.decide(organizationId, deploymentId, userId, status, expectedVersion)) {
            throw new VersionConflictException();
        }
        deployments.updateReleaseStatus(organizationId, current.releaseId(),
                approve ? "APPROVED" : "READY_FOR_APPROVAL");
        deployments.audit(organizationId, "USER", userId, "deployment.approval.decided",
                deploymentId, status, requestId, null);
        return find(organizationId, deploymentId);
    }

    private DeploymentView create(long organizationId, long releaseId, long requestedBy,
            boolean simulateFailure, String idempotencyKey, String approvalSource, String agentApprovalId,
            String traceId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("deployment idempotency key is required");
        }
        ReleaseView release = requireDeployable(organizationId, releaseId);
        String normalized = "{\"mode\":\"SIMULATED\",\"releaseId\":" + releaseId
                + ",\"simulateFailure\":" + simulateFailure + "}";
        String argumentHash = sha256(normalized);
        var replay = deployments.findByIdempotencyKey(organizationId, idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.orElseThrow().argumentHash().equals(argumentHash)) {
                throw new ai.forge.server.workitem.domain.IdempotencyConflictException();
            }
            return replay.orElseThrow();
        }
        long ttlMinutes = releases.loadFacts(organizationId, releaseId).approvalTtlMinutes();
        DeploymentView deployment = deployments.request(organizationId, releaseId, requestedBy,
                release.version(), release.latestPrecheck().id(), argumentHash, simulateFailure,
                idempotencyKey, Instant.now().plus(Duration.ofMinutes(ttlMinutes)), approvalSource, agentApprovalId);
        deployments.updateReleaseStatus(organizationId, releaseId,
                "AGENT_TOOL".equals(approvalSource) ? "APPROVED" : "READY_FOR_APPROVAL");
        deployments.audit(organizationId, "AGENT_TOOL".equals(approvalSource) ? "AGENT" : "USER",
                requestedBy, "deployment.requested", deployment.id(), deployment.status(), traceId,
                "AGENT_TOOL".equals(approvalSource) ? traceId : null);
        return deployment;
    }

    private ReleaseView requireDeployable(long organizationId, long releaseId) {
        ReleaseView release = releases.find(organizationId, releaseId)
                .orElseThrow(ResourceNotFoundException::new);
        if (release.latestPrecheck() == null || !"PASS".equals(release.latestPrecheck().status())
                || !release.latestPrecheck().current()) {
            throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "PRECHECK_NOT_CURRENT_PASS",
                    "Deployment requires the latest current PASS Precheck");
        }
        return release;
    }

    private DeploymentView find(long organizationId, long deploymentId) {
        return deployments.find(organizationId, deploymentId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
