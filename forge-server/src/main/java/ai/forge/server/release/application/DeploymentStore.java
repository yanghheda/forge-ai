package ai.forge.server.release.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeploymentStore {

    DeploymentView request(long workspaceId, long projectId, long releaseId, long requestedBy,
            long releaseVersion, long precheckId, String argumentHash, boolean simulateFailure,
            String idempotencyKey, Instant expiresAt, String approvalSource, String agentApprovalId);

    Optional<DeploymentView> find(long workspaceId, long projectId, long deploymentId);

    List<DeploymentView> list(long workspaceId, long projectId, long releaseId);

    Optional<DeploymentView> findByIdempotencyKey(long workspaceId, long projectId, String idempotencyKey);

    List<DeploymentView> approved();

    boolean decide(long workspaceId, long projectId, long deploymentId, long approverUserId,
            String status, long expectedVersion);

    boolean expire(long workspaceId, long projectId, long deploymentId, long expectedVersion);

    boolean claim(long deploymentId, long expectedVersion);

    void complete(long deploymentId, boolean succeeded, String resultCode, String resultSummary);

    void updateReleaseStatus(long workspaceId, long projectId, long releaseId, String status);

    void completeRequirements(long workspaceId, long projectId, long releaseId, long actorId, long deploymentId);

    void audit(long workspaceId, long projectId, String actorType, long actorId, String action,
            long deploymentId, String result, String requestId, String runId);
}
