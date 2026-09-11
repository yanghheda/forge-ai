package ai.forge.server.release.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeploymentStore {

    DeploymentView request(long organizationId, long releaseId, long requestedBy,
            long releaseVersion, long precheckId, String argumentHash, boolean simulateFailure,
            String idempotencyKey, Instant expiresAt, String approvalSource, String agentApprovalId);

    Optional<DeploymentView> find(long organizationId, long deploymentId);

    List<DeploymentView> list(long organizationId, long releaseId);

    Optional<DeploymentView> findByIdempotencyKey(long organizationId, String idempotencyKey);

    List<DeploymentView> approved();

    boolean decide(long organizationId, long deploymentId, long approverUserId,
            String status, long expectedVersion);

    boolean expire(long organizationId, long deploymentId, long expectedVersion);

    boolean claim(long deploymentId, long expectedVersion);

    void complete(long deploymentId, boolean succeeded, String resultCode, String resultSummary);

    void updateReleaseStatus(long organizationId, long releaseId, String status);

    void completeRequirements(long organizationId, long releaseId, long actorId, long deploymentId);

    void audit(long organizationId, String actorType, long actorId, String action,
            long deploymentId, String result, String requestId, String runId);
}
