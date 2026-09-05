package ai.forge.server.gitlab.application;

import ai.forge.server.gitlab.domain.Branch;
import ai.forge.server.gitlab.domain.MergeRequest;
import ai.forge.server.workitem.domain.WorkItem;
import java.util.Optional;

public interface DevelopmentStore {

    Optional<WorkItem> findWorkItem(long workspaceId, long projectId, long workItemId);

    boolean hasActiveProjectMember(long workspaceId, long projectId, long userId);

    WorkItem createDevTask(long workspaceId, long projectId, long actorId, long requirementId,
            String title, String description, Long assigneeUserId);

    DevelopmentContext begin(long workspaceId, long projectId, long devTaskId, String targetBranch,
            String idempotencyKey);

    DevelopmentResult complete(
            DevelopmentContext context, Branch branch, MergeRequest mergeRequest, boolean reconciled);

    WorkItem completeTask(
            long workspaceId,
            long projectId,
            long taskId,
            long expectedVersion);
}
