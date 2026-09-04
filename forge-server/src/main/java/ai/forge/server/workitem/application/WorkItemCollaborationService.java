package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.domain.ActivityItem;
import ai.forge.server.workitem.domain.RelationConflictException;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemRelation;
import ai.forge.server.workitem.domain.WorkItemRelationType;
import ai.forge.server.workitem.domain.WorkItemLabel;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class WorkItemCollaborationService {

    /* 读取工作项类型并强制 Workspace/Project scope。 */
    private final WorkItemStore workItemStore;

    /* 在资源加载后执行最终项目权限检查。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 持久化关系、评论并读取统一活动投影。 */
    private final WorkItemCollaborationStore collaborationStore;

    public WorkItemCollaborationService(
            WorkItemStore workItemStore,
            PermissionEvaluator permissionEvaluator,
            WorkItemCollaborationStore collaborationStore) {
        this.workItemStore = workItemStore;
        this.permissionEvaluator = permissionEvaluator;
        this.collaborationStore = collaborationStore;
    }

    @Transactional
    public WorkItemRelation createRelation(
            long userId,
            long workspaceId,
            long projectId,
            long sourceId,
            long targetId,
            WorkItemRelationType relationType) {
        if (sourceId == targetId) {
            throw new IllegalArgumentException("Self relation is forbidden");
        }
        WorkItem source = requireItem(workspaceId, projectId, sourceId);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, source.type().permissionResource() + ".edit");
        requireItem(workspaceId, projectId, targetId);
        if (collaborationStore.relationExists(workspaceId, projectId, sourceId, targetId, relationType)) {
            throw new RelationConflictException();
        }
        return collaborationStore.createRelation(
                workspaceId, projectId, sourceId, targetId, relationType, userId);
    }

    public List<WorkItemRelation> relations(
            long userId, long workspaceId, long projectId, long workItemId) {
        WorkItem item = requireItem(workspaceId, projectId, workItemId);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".read");
        return collaborationStore.findRelations(workspaceId, projectId, workItemId);
    }

    @Transactional
    public void addLabel(
            long userId,
            long workspaceId,
            long projectId,
            long workItemId,
            WorkItemLabel label) {
        WorkItem item = requireItem(workspaceId, projectId, workItemId);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".edit");
        collaborationStore.addLabel(workspaceId, projectId, workItemId, label, userId);
    }

    @Transactional
    public ActivityItem comment(
            long userId, long workspaceId, long projectId, long workItemId, String body) {
        WorkItem item = requireItem(workspaceId, projectId, workItemId);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".read");
        String normalized = body == null ? "" : body.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Comment body is required");
        }
        return collaborationStore.createComment(workspaceId, projectId, workItemId, userId, normalized);
    }

    public List<ActivityItem> activity(
            long userId, long workspaceId, long projectId, long workItemId) {
        WorkItem item = requireItem(workspaceId, projectId, workItemId);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".read");
        return collaborationStore.findActivity(workspaceId, projectId, workItemId);
    }

    private WorkItem requireItem(long workspaceId, long projectId, long workItemId) {
        return workItemStore.findByIdAndScope(workspaceId, projectId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
    }

}
