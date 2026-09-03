package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class WorkItemQueryService {

    /* 对详情按实际类型授权，对混合列表验证全部本轮读取能力。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 强制 Workspace 与 Project scope 的工作项查询端口。 */
    private final WorkItemStore workItemStore;

    public WorkItemQueryService(PermissionEvaluator permissionEvaluator, WorkItemStore workItemStore) {
        this.permissionEvaluator = permissionEvaluator;
        this.workItemStore = workItemStore;
    }

    public WorkItem get(long userId, long workspaceId, long projectId, long workItemId) {
        WorkItem item = workItemStore.findByIdAndScope(workspaceId, projectId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireProject(
                userId, workspaceId, projectId, item.type().permissionResource() + ".read");
        return item;
    }

    public WorkItemPage list(
            long userId,
            long workspaceId,
            long projectId,
            WorkItemType type,
            WorkItemStatus status,
            int page,
            int pageSize) {
        if (type == null) {
            for (String resource : new String[] {"requirement", "ux", "task"}) {
                permissionEvaluator.requireProject(userId, workspaceId, projectId, resource + ".read");
            }
        } else {
            permissionEvaluator.requireProject(
                    userId, workspaceId, projectId, type.permissionResource() + ".read");
        }
        return workItemStore.findPage(workspaceId, projectId, type, status, page, pageSize);
    }
}
