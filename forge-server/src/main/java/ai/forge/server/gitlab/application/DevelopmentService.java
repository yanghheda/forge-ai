package ai.forge.server.gitlab.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class DevelopmentService {

    /* 在读取项目事实前执行 task/repo 的最终服务端授权。 */
    private final PermissionEvaluator permissions;

    /* 用短事务记录本地意图、关联快照并转换工作项状态。 */
    private final DevelopmentStore store;

    /* 隔离远端 GitLab 写入、查询与标准 DTO。 */
    private final SourceControlProvider sourceControl;

    public DevelopmentService(
            PermissionEvaluator permissions,
            DevelopmentStore store,
            SourceControlProvider sourceControl) {
        this.permissions = permissions;
        this.store = store;
        this.sourceControl = sourceControl;
    }

    public WorkItem createDevTask(long userId, long workspaceId, long projectId, long requirementId,
            String title, String description, Long assigneeUserId) {
        permissions.requireProject(userId, workspaceId, projectId, "task.create");
        WorkItem requirement = store.findWorkItem(workspaceId, projectId, requirementId)
                .orElseThrow(ResourceNotFoundException::new);
        if (requirement.type() != WorkItemType.REQUIREMENT
                || requirement.status() != WorkItemStatus.READY_FOR_DEV) {
            throw new DevelopmentStateException();
        }
        if (assigneeUserId != null
                && !store.hasActiveProjectMember(workspaceId, projectId, assigneeUserId)) {
            throw new ResourceNotFoundException();
        }
        return store.createDevTask(
                workspaceId, projectId, userId, requirementId, normalizeTitle(title), normalizeDescription(description),
                assigneeUserId);
    }

    public DevelopmentResult start(long userId, long workspaceId, long projectId, long devTaskId,
            String requestedTargetBranch, String idempotencyKey) {
        permissions.requireProject(userId, workspaceId, projectId, "task.edit");
        permissions.requireProject(userId, workspaceId, projectId, "repo.read");
        DevelopmentContext context = store.begin(
                workspaceId, projectId, devTaskId, requestedTargetBranch, requireText(idempotencyKey));
        String targetBranch = requestedTargetBranch == null || requestedTargetBranch.isBlank()
                ? context.defaultBranch()
                : requestedTargetBranch.trim();
        if (targetBranch == null || targetBranch.isBlank()) {
            throw new DevelopmentStateException();
        }
        if (context.baseSha() == null || context.baseSha().isBlank()) {
            Branch base = sourceControl.findBranch(context, targetBranch)
                    .orElseThrow(DevelopmentStateException::new);
            context = new DevelopmentContext(
                    context.task(), context.requirementId(), context.repositoryId(), context.connectionId(),
                    context.baseUrl(), context.remoteProjectId(), context.defaultBranch(), base.commitSha(),
                    context.token(), context.idempotencyKey(), context.completed());
        }
        String branchName = branchName(context.task());
        String baseSha = context.baseSha();
        boolean reconciled = false;

        Optional<Branch> existingBranch = sourceControl.findBranch(context, branchName);
        Branch branch;
        if (existingBranch.isPresent()) {
            branch = requireSameBase(existingBranch.get(), baseSha);
            reconciled = true;
        } else {
            try {
                branch = sourceControl.createBranch(context, branchName, baseSha, idempotencyKey);
            } catch (GitLabRemoteException exception) {
                if (!canReconcile(exception)) {
                    throw exception;
                }
                branch = sourceControl.findBranch(context, branchName)
                        .map(value -> requireSameBase(value, baseSha))
                        .orElseThrow(() -> exception);
                reconciled = true;
            }
        }

        Optional<MergeRequest> existingMr = sourceControl.findOpenMergeRequest(context, branchName, targetBranch);
        MergeRequest mergeRequest;
        if (existingMr.isPresent()) {
            mergeRequest = existingMr.get();
            reconciled = true;
        } else {
            try {
                mergeRequest = sourceControl.createMergeRequest(
                        context, branchName, targetBranch, context.task().title(), idempotencyKey);
            } catch (GitLabRemoteException exception) {
                if (!canReconcile(exception)) {
                    throw exception;
                }
                mergeRequest = sourceControl.findOpenMergeRequest(context, branchName, targetBranch)
                        .orElseThrow(() -> exception);
                reconciled = true;
            }
        }
        return store.complete(context, branch, mergeRequest, reconciled);
    }

    public WorkItem completeTask(
            long userId,
            long workspaceId,
            long projectId,
            long devTaskId,
            long expectedVersion) {
        permissions.requireProject(userId, workspaceId, projectId, "task.edit");
        return store.completeTask(workspaceId, projectId, devTaskId, expectedVersion);
    }

    static String branchName(WorkItem task) {
        String normalized = Normalizer.normalize(task.title(), Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String slug = normalized.isBlank() ? "task" : normalized;
        String prefix = "feature/" + task.itemKey().toLowerCase(Locale.ROOT) + "-";
        return prefix + slug.substring(0, Math.min(slug.length(), 255 - prefix.length()));
    }

    private static Branch requireSameBase(Branch branch, String baseSha) {
        if (!branch.commitSha().equals(baseSha)) {
            throw new RemoteResourceConflictException();
        }
        return branch;
    }

    private static boolean canReconcile(GitLabRemoteException exception) {
        return "GITLAB_TIMEOUT".equals(exception.code()) || "GITLAB_CONFLICT".equals(exception.code());
    }

    private static String normalizeTitle(String value) {
        String title = requireText(value);
        if (title.length() > 255) {
            throw new IllegalArgumentException("title too long");
        }
        return title;
    }

    private static String normalizeDescription(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim();
    }
}
