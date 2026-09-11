package ai.forge.server.gitlab.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.application.DevelopmentContext;
import ai.forge.server.gitlab.application.DevelopmentResult;
import ai.forge.server.gitlab.application.DevelopmentStore;
import ai.forge.server.gitlab.application.PendingDevelopmentOperation;
import ai.forge.server.gitlab.application.EncryptedSecret;
import ai.forge.server.gitlab.application.SecretService;
import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.organization.application.OrganizationStore;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.IdempotencyConflictException;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.common.domain.VersionConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisDevelopmentStore implements DevelopmentStore {

    /* 复用工作项聚合的编号分配与 scope 查询。 */
    private final WorkItemStore workItems;
    /* 执行显式带公司作用域的开发关联 SQL。 */
    private final DevelopmentMapper mapper;
    /* 仅在短事务结束前解密，明文只进入随后当前调用栈。 */
    private final SecretService secrets;
    /* 验证可选负责人仍是当前公司的有效成员。 */
    private final OrganizationStore organizations;

    public MybatisDevelopmentStore(
            WorkItemStore workItems, DevelopmentMapper mapper, SecretService secrets, OrganizationStore organizations) {
        this.workItems = workItems;
        this.mapper = mapper;
        this.secrets = secrets;
        this.organizations = organizations;
    }

    @Override
    public Optional<WorkItem> findWorkItem(long organizationId, long workItemId) {
        return workItems.findByIdAndScope(organizationId, workItemId);
    }

    @Override
    public boolean hasActiveOrganizationMember(long organizationId, long userId) {
        return organizations.hasActiveMember(organizationId, userId);
    }

    @Override
    public WorkItem createDevTask(long organizationId, long actorId, long requirementId,
            String title, String description, Long assigneeUserId) {
        return workItems.createChild(organizationId, actorId, WorkItemType.DEV_TASK, requirementId,
                title, description, WorkItemStatus.TODO, WorkItemPriority.MEDIUM, assigneeUserId, null);
    }

    @Override
    @Transactional
    public DevelopmentContext begin(long organizationId, long devTaskId, String targetBranch,
            String idempotencyKey) {
        WorkItem task = workItems.findByIdAndScope(organizationId, devTaskId)
                .orElseThrow(ResourceNotFoundException::new);
        Map<String, Object> row = mapper.findContext(organizationId, devTaskId).stream()
                .findFirst().orElseThrow(ResourceNotFoundException::new);
        String target = targetBranch == null || targetBranch.isBlank()
                ? text(row, "default_branch") : targetBranch.trim();
        String requestHash = sha256(devTaskId + "\n" + target);
        try {
            mapper.insertOperation(organizationId, devTaskId, idempotencyKey, requestHash, target);
        } catch (DuplicateKeyException exception) {
            Map<String, Object> operation = mapper.findOperation(organizationId, idempotencyKey).stream()
                    .findFirst().orElseThrow();
            if (!requestHash.equals(text(operation, "request_hash"))) {
                throw new IdempotencyConflictException();
            }
        }
        EncryptedSecret encrypted = new EncryptedSecret(text(row, "ciphertext"), text(row, "iv"),
                ((Number) row.get("key_version")).intValue(), text(row, "fingerprint"));
        String token = secrets.decrypt(organizationId, text(row, "type"), encrypted);
        long repositoryId = number(row, "repository_id");
        String cachedSha = mapper.findCachedSha(organizationId, repositoryId, target).stream().findFirst().orElse("");
        return new DevelopmentContext(task, number(row, "parent_id"), repositoryId,
                number(row, "connection_id"), text(row, "base_url"), text(row, "remote_project_id"),
                text(row, "default_branch"), cachedSha, token, idempotencyKey, false);
    }

    @Override
    @Transactional
    public DevelopmentResult complete(DevelopmentContext context, Branch remoteBranch,
            MergeRequest remoteMergeRequest, boolean reconciled) {
        mapper.upsertBranch(context.task().organizationId(), context.repositoryId(), context.task().id(),
                remoteBranch.name(), remoteBranch.commitSha(), remoteBranch.remoteUpdatedAt());
        Branch branch = mapper.findBranch(context.task().organizationId(), context.repositoryId(), remoteBranch.name())
                .stream().findFirst().map(this::branch).orElseThrow();
        mapper.upsertMergeRequest(context.task().organizationId(), context.repositoryId(), context.task().id(),
                remoteMergeRequest.remoteMrIid(), remoteMergeRequest.title(), remoteMergeRequest.sourceBranch(),
                remoteMergeRequest.targetBranch(), remoteMergeRequest.state(), remoteMergeRequest.webUrl(),
                remoteMergeRequest.authorExternalId(), remoteMergeRequest.headSha(), remoteMergeRequest.mergeStatus(),
                remoteMergeRequest.remoteUpdatedAt());
        MergeRequest mergeRequest = mapper.findMergeRequest(context.task().organizationId(), context.repositoryId(),
                remoteMergeRequest.remoteMrIid()).stream().findFirst().map(this::mergeRequest).orElseThrow();
        if (mapper.startTask(context.task().organizationId(), context.task().id()) != 1
                || mapper.startRequirement(context.task().organizationId(), context.requirementId()) != 1) {
            throw new IllegalStateException("development status changed during remote operation");
        }
        mapper.completeOperation(context.task().organizationId(), context.task().id(),
                context.idempotencyKey(), branch.id(), mergeRequest.id());
        return new DevelopmentResult(branch, mergeRequest, reconciled);
    }

    @Override
    @Transactional
    public WorkItem completeTask(
            long organizationId,
            long taskId,
            long expectedVersion) {
        workItems.findByIdAndScope(organizationId, taskId)
                .orElseThrow(ResourceNotFoundException::new);
        if (mapper.completeTask(organizationId, taskId, expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        return workItems.findByIdAndScope(organizationId, taskId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public Optional<PendingDevelopmentOperation> findPendingOperation() {
        Optional<PendingDevelopmentOperation> operation = mapper.findPendingOperation().stream().findFirst()
                .map(row -> new PendingDevelopmentOperation(
                number(row, "organization_id"),
                number(row, "work_item_id"),
                text(row, "target_branch"),
                text(row, "idempotency_key"),
                ((Number) row.get("attempt_count")).intValue()));
        operation.ifPresent(value -> mapper.leaseOperation(
                value.organizationId(), value.workItemId(), value.idempotencyKey()));
        return operation;
    }

    @Override
    @Transactional
    public void defer(PendingDevelopmentOperation operation, String errorCode) {
        mapper.deferOperation(
                operation.organizationId(),
                operation.workItemId(),
                operation.idempotencyKey(),
                errorCode);
    }

    @Override
    @Transactional
    public void requireManualRecovery(PendingDevelopmentOperation operation, String errorCode) {
        mapper.requireManualRecovery(
                operation.organizationId(),
                operation.workItemId(),
                operation.idempotencyKey(),
                errorCode);
    }

    private Branch branch(Map<String, Object> row) {
        return new Branch(number(row, "id"), number(row, "organization_id"), number(row, "repository_id"),
                number(row, "work_item_id"), text(row, "name"), text(row, "commit_sha"), text(row, "status"),
                (LocalDateTime) row.get("remote_updated_at"), (LocalDateTime) row.get("last_synced_at"));
    }

    private MergeRequest mergeRequest(Map<String, Object> row) {
        return new MergeRequest(number(row, "id"), number(row, "organization_id"), number(row, "repository_id"),
                number(row, "work_item_id"), number(row, "remote_mr_iid"), text(row, "title"),
                text(row, "source_branch"), text(row, "target_branch"), text(row, "state"), text(row, "web_url"),
                nullable(row, "author_external_id"), nullable(row, "head_sha"), nullable(row, "merge_status"),
                (LocalDateTime) row.get("remote_updated_at"), (LocalDateTime) row.get("last_synced_at"),
                number(row, "version"));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private static String nullable(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
