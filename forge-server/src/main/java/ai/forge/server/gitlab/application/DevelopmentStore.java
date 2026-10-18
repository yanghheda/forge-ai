package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.workitem.domain.WorkItem;
import java.util.Optional;

public interface DevelopmentStore {

    Optional<WorkItem> findWorkItem(long organizationId, long workItemId);

    boolean hasActiveOrganizationMember(long organizationId, long userId);

    /* 判断 Dev Task 是否已具备可用的开发上下文（需求处于研发中且公司存在 ACTIVE 的仓库连接与凭据）。 */
    boolean developmentContextAvailable(long organizationId, long devTaskId);

    WorkItem createDevTask(long organizationId, long actorId, long requirementId,
            String title, String description, Long assigneeUserId);

    DevelopmentContext begin(long organizationId, long devTaskId, String targetBranch,
            String idempotencyKey);

    DevelopmentResult complete(
            DevelopmentContext context, Branch branch, MergeRequest mergeRequest, boolean reconciled);

    WorkItem completeTask(
            long organizationId,
            long taskId,
            long expectedVersion);

    /* 读取 Dev Task 最新 MR 快照是否已合并；无 MR 快照时视为未合并。 */
    boolean hasMergedMergeRequest(long organizationId, long devTaskId);

    default Optional<PendingDevelopmentOperation> findPendingOperation() {
        return Optional.empty();
    }

    default void defer(PendingDevelopmentOperation operation, String errorCode) {
        /* 无持久实现的测试 Store 不参与后台恢复。 */
    }

    default void requireManualRecovery(PendingDevelopmentOperation operation, String errorCode) {
        /* 无持久实现的测试 Store 不参与后台恢复。 */
    }
}
