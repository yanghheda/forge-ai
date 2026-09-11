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

    /* 读取工作项类型并强制公司作用域。 */
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
            long organizationId,
            long sourceId,
            long targetId,
            WorkItemRelationType relationType) {
        if (sourceId == targetId) {
            throw new IllegalArgumentException("Self relation is forbidden");
        }
        WorkItem source = requireItem(organizationId, sourceId);
        permissionEvaluator.requireOrganization(
                userId, organizationId, source.type().permissionResource() + ".edit");
        requireItem(organizationId, targetId);
        if (collaborationStore.relationExists(organizationId, sourceId, targetId, relationType)) {
            throw new RelationConflictException();
        }
        return collaborationStore.createRelation(
                organizationId, sourceId, targetId, relationType, userId);
    }

    public List<WorkItemRelation> relations(
            long userId, long organizationId, long workItemId) {
        WorkItem item = requireItem(organizationId, workItemId);
        permissionEvaluator.requireOrganization(
                userId, organizationId, item.type().permissionResource() + ".read");
        return collaborationStore.findRelations(organizationId, workItemId);
    }

    @Transactional
    public void addLabel(
            long userId,
            long organizationId,
            long workItemId,
            WorkItemLabel label) {
        WorkItem item = requireItem(organizationId, workItemId);
        permissionEvaluator.requireOrganization(
                userId, organizationId, item.type().permissionResource() + ".edit");
        collaborationStore.addLabel(organizationId, workItemId, label, userId);
    }

    @Transactional
    public ActivityItem comment(
            long userId, long organizationId, long workItemId, String body) {
        WorkItem item = requireItem(organizationId, workItemId);
        permissionEvaluator.requireOrganization(
                userId, organizationId, item.type().permissionResource() + ".read");
        String normalized = body == null ? "" : body.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Comment body is required");
        }
        return collaborationStore.createComment(organizationId, workItemId, userId, normalized);
    }

    public List<ActivityItem> activity(
            long userId, long organizationId, long workItemId) {
        WorkItem item = requireItem(organizationId, workItemId);
        permissionEvaluator.requireOrganization(
                userId, organizationId, item.type().permissionResource() + ".read");
        return collaborationStore.findActivity(organizationId, workItemId);
    }

    private WorkItem requireItem(long organizationId, long workItemId) {
        return workItemStore.findByIdAndScope(organizationId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
    }

}
