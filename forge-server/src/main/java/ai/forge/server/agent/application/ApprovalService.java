package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.ApprovalStatus;
import ai.forge.server.agent.domain.ToolExecutionRejectedException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.release.application.ReleaseStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class ApprovalService {

    /* 审批 TTL；短时冻结限制陈旧授权窗口。 */
    private static final java.time.Duration APPROVAL_TTL = java.time.Duration.ofMinutes(15);

    /* 审批及恢复 Outbox 的显式 scope SQL。 */
    private final ApprovalStore store;
    /* 加解密完整规范化参数。 */
    private final ApprovalCipher cipher;
    /* 序列化资源版本与规范化参数。 */
    private final ObjectMapper objectMapper;
    /* 决策与恢复时读取最新项目权限。 */
    private final PermissionEvaluator permissionEvaluator;
    /* 读取受影响 Work Item 当前版本。 */
    private final WorkItemStore workItemStore;
    /* 拒绝审批时把 Run 推进到稳定终态。 */
    private final AgentRunStore runStore;
    /* 冻结 HIGH 部署所影响的 Release 聚合版本。 */
    private final ReleaseStore releaseStore;

    public ApprovalService(ApprovalStore store, ApprovalCipher cipher, ObjectMapper objectMapper,
            PermissionEvaluator permissionEvaluator, WorkItemStore workItemStore, AgentRunStore runStore,
            ReleaseStore releaseStore) {
        this.store = store;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.workItemStore = workItemStore;
        this.runStore = runStore;
        this.releaseStore = releaseStore;
    }

    @Transactional
    public ApprovalSnapshot request(AgentRun run, ToolContract contract, String toolCallId, JsonNode arguments) {
        String normalized = normalized(arguments);
        String hash = sha256(normalized);
        List<ApprovalSnapshot.ResourceVersion> resources = resourceVersions(run, contract.name(), arguments);
        String approvalId = AgentRunIdGenerator.next();
        try {
            store.insert(approvalId, run.organizationId(), run.id(), toolCallId,
                    contract.riskLevel(), run.userId(), contract.name(), contract.version(), hash,
                    cipher.encrypt(normalized), json(resources), contract.highRisk()
                            ? "HIGH Tool always requires another user's approval"
                            : "MEDIUM Tool requires explicit confirmation",
                    LocalDateTime.ofInstant(Instant.now().plus(APPROVAL_TTL), ZoneOffset.UTC));
            store.insertWaitingToolCall(run.organizationId(), run.id(), toolCallId,
                    contract.name(), contract.version(), contract.riskLevel(), hash, normalized,
                    run.id() + ":" + toolCallId, approvalId);
            if (!store.markRunWaiting(run.organizationId(), run.id())) {
                throw new VersionConflictException();
            }
            store.insertRequiredEvent(run.organizationId(), run.id(), approvalId,
                    contract.name(), "agent:" + run.id());
            return find(run.organizationId(), approvalId);
        } catch (DuplicateKeyException exception) {
            ApprovalSnapshot existing = findByCall(run.organizationId(), run.id(), toolCallId);
            requireFrozenInput(existing, contract, hash);
            return existing;
        }
    }

    public ApprovalSnapshot get(long userId, long organizationId, String approvalId) {
        permissionEvaluator.requireOrganization(userId, organizationId, "agent.run");
        return expireIfNeeded(find(organizationId, approvalId), organizationId,
                "approval-expiry:" + approvalId);
    }

    public ApprovalSnapshot getForRun(long userId, long organizationId, String runId) {
        permissionEvaluator.requireOrganization(userId, organizationId, "agent.run");
        Map<String, Object> row = store.findByRun(organizationId, runId).stream().findFirst()
                .orElseThrow(ResourceNotFoundException::new);
        ApprovalSnapshot approval = snapshot(row);
        return expireIfNeeded(approval, organizationId, "approval-expiry:" + approval.id());
    }

    @Transactional
    public ApprovalSnapshot decide(long userId, long organizationId, String approvalId,
            boolean approve, long expectedVersion, String requestId) {
        permissionEvaluator.requireOrganization(userId, organizationId, "approval.decide");
        ApprovalSnapshot current = find(organizationId, approvalId);
        if (current.requestedBy() == userId) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "SELF_APPROVAL_FORBIDDEN", "Requester cannot approve their own tool call");
        }
        store.expire(organizationId, approvalId);
        current = find(organizationId, approvalId);
        if (current.status() == ApprovalStatus.EXPIRED) {
            finishWithoutExecution(current, organizationId, "approval.expired", "EXPIRED",
                    "APPROVAL_EXPIRED", requestId);
            return current;
        }
        if (current.status() != ApprovalStatus.PENDING || !store.decide(organizationId, approvalId,
                userId, approve ? "APPROVED" : "REJECTED", expectedVersion)) {
            throw new VersionConflictException();
        }
        if (approve) {
            store.insertResumeOutbox(organizationId, current.runId(), approvalId);
            store.markRunResuming(organizationId, current.runId());
        } else {
            store.markRunResuming(organizationId, current.runId());
        }
        store.insertDecisionEvent(organizationId, current.runId(), approvalId,
                approve ? "approval.approved" : "approval.rejected", approve ? "APPROVED" : "REJECTED", requestId);
        if (!approve) {
            runStore.fail(organizationId, current.runId(), requestId, "APPROVAL_REJECTED");
        }
        return find(organizationId, approvalId);
    }

    @Transactional
    public ApprovalSnapshot cancel(long userId, long organizationId, String approvalId,
            long expectedVersion, String requestId) {
        permissionEvaluator.requireOrganization(userId, organizationId, "agent.run");
        ApprovalSnapshot current = expireIfNeeded(find(organizationId, approvalId),
                organizationId, requestId);
        if (current.requestedBy() != userId) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "APPROVAL_CANCEL_FORBIDDEN", "Only the requester may cancel a pending approval");
        }
        if (current.status() != ApprovalStatus.PENDING || !store.decide(organizationId, approvalId,
                userId, "CANCELLED", expectedVersion)) {
            throw new VersionConflictException();
        }
        current = find(organizationId, approvalId);
        finishWithoutExecution(current, organizationId, "approval.cancelled", "CANCELLED",
                "APPROVAL_CANCELLED", requestId);
        return current;
    }

    public JsonNode requireApproved(AgentRun run, ToolContract contract, String toolCallId, JsonNode supplied) {
        ApprovalSnapshot approval = findByCall(run.organizationId(), run.id(), toolCallId);
        String suppliedHash = sha256(normalized(supplied));
        requireFrozenInput(approval, contract, suppliedHash);
        if (approval.status() != ApprovalStatus.APPROVED || approval.expiresAt().isBefore(Instant.now())) {
            throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "APPROVAL_NOT_ACTIVE",
                    "Approval is not approved or has expired");
        }
        permissionEvaluator.requireOrganization(run.userId(), run.organizationId(), contract.requiredPermission());
        requireResourceVersions(run, approval.resources());
        Map<String, Object> row = rowByCall(run.organizationId(), run.id(), toolCallId);
        return readTree(cipher.decrypt(row.get("frozen_arguments_encrypted").toString()));
    }

    public boolean existsForCall(long organizationId, String runId, String toolCallId) {
        return !store.findByCall(organizationId, runId, toolCallId).isEmpty();
    }

    public String approvalIdForCall(long organizationId, String runId, String toolCallId) {
        return findByCall(organizationId, runId, toolCallId).id();
    }

    public void complete(long organizationId, String runId, String toolCallId, JsonNode result) {
        store.completeToolCall(organizationId, runId, toolCallId, result.toString());
    }

    private void requireFrozenInput(ApprovalSnapshot approval, ToolContract contract, String hash) {
        if (!approval.toolName().equals(contract.name()) || approval.toolVersion() != contract.version()
                || !approval.argumentHash().equals(hash)) {
            throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "APPROVAL_INPUT_CHANGED",
                    "Tool, contract version, or arguments changed after approval request");
        }
    }

    private ApprovalSnapshot expireIfNeeded(ApprovalSnapshot approval, long organizationId,
            String requestId) {
        if (approval.status() == ApprovalStatus.PENDING && !approval.expiresAt().isAfter(Instant.now())) {
            store.expire(organizationId, approval.id());
            ApprovalSnapshot expired = find(organizationId, approval.id());
            finishWithoutExecution(expired, organizationId, "approval.expired", "EXPIRED",
                    "APPROVAL_EXPIRED", requestId);
            return expired;
        }
        return approval;
    }

    private void finishWithoutExecution(ApprovalSnapshot approval, long organizationId,
            String eventType, String status, String errorCode, String requestId) {
        store.markRunResuming(organizationId, approval.runId());
        store.insertDecisionEvent(organizationId, approval.runId(), approval.id(),
                eventType, status, requestId);
        runStore.fail(organizationId, approval.runId(), requestId, errorCode);
    }

    private List<ApprovalSnapshot.ResourceVersion> resourceVersions(
            AgentRun run, String toolName, JsonNode arguments) {
        if (List.of("create_ux_task", "create_prd_document", "create_ux_document").contains(toolName)) {
            long id = arguments.path("requirementId").asLong();
            var item = workItemStore.findByIdAndScope(run.organizationId(), id)
                    .orElseThrow(ResourceNotFoundException::new);
            return List.of(new ApprovalSnapshot.ResourceVersion("WORK_ITEM", Long.toString(id), item.version()));
        }
        if ("deploy_release".equals(toolName)) {
            long id = arguments.path("releaseId").asLong();
            var release = releaseStore.find(run.organizationId(), id)
                    .orElseThrow(ResourceNotFoundException::new);
            return List.of(new ApprovalSnapshot.ResourceVersion("RELEASE", Long.toString(id), release.version()));
        }
        return List.of();
    }

    private void requireResourceVersions(AgentRun run, List<ApprovalSnapshot.ResourceVersion> resources) {
        for (ApprovalSnapshot.ResourceVersion resource : resources) {
            if ("RELEASE".equals(resource.type())) {
                long current = releaseStore.find(run.organizationId(), Long.parseLong(resource.id()))
                        .orElseThrow(ResourceNotFoundException::new).version();
                if (current != resource.version()) {
                    throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "RESOURCE_VERSION_CHANGED",
                            "Resource changed after approval request");
                }
                continue;
            }
            if (!"WORK_ITEM".equals(resource.type())) {
                throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "RESOURCE_VERSION_CHANGED",
                        "Unsupported frozen resource type");
            }
            long current = workItemStore.findByIdAndScope(run.organizationId(), Long.parseLong(resource.id()))
                    .orElseThrow(ResourceNotFoundException::new).version();
            if (current != resource.version()) {
                throw new ToolExecutionRejectedException(HttpStatus.CONFLICT.value(), "RESOURCE_VERSION_CHANGED",
                        "Resource changed after approval request");
            }
        }
    }

    private ApprovalSnapshot find(long organizationId, String approvalId) {
        return store.find(organizationId, approvalId).stream().findFirst()
                .map(this::snapshot).orElseThrow(ResourceNotFoundException::new);
    }

    private ApprovalSnapshot findByCall(long organizationId, String runId, String toolCallId) {
        return snapshot(rowByCall(organizationId, runId, toolCallId));
    }

    private Map<String, Object> rowByCall(long organizationId, String runId, String toolCallId) {
        return store.findByCall(organizationId, runId, toolCallId).stream().findFirst()
                .orElseThrow(ResourceNotFoundException::new);
    }

    private ApprovalSnapshot snapshot(Map<String, Object> row) {
        try {
            List<ApprovalSnapshot.ResourceVersion> resources = objectMapper.readValue(
                    row.get("resource_versions_json").toString(), new TypeReference<>() { });
            return new ApprovalSnapshot(row.get("id").toString(), row.get("run_id").toString(),
                    row.get("tool_call_id").toString(), row.get("tool_name").toString(),
                    ((Number) row.get("tool_version")).intValue(), row.get("risk_level").toString(),
                    ApprovalStatus.valueOf(row.get("status").toString()),
                    ((Number) row.get("requested_by")).longValue(),
                    row.get("approver_user_id") == null ? null : ((Number) row.get("approver_user_id")).longValue(),
                    row.get("argument_hash").toString(), resources, row.get("reason").toString(),
                    ((LocalDateTime) row.get("expires_at")).toInstant(ZoneOffset.UTC),
                    ((Number) row.get("version")).longValue());
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted approval is invalid", exception);
        }
    }

    private String normalized(JsonNode arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tool arguments cannot be normalized", exception);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Approval resources cannot be serialized", exception);
        }
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Frozen approval arguments are invalid", exception);
        }
    }
}
