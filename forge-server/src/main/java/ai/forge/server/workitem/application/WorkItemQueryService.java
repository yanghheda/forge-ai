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

    /* 强制公司作用域的工作项查询端口。 */
    private final WorkItemStore workItemStore;

    public WorkItemQueryService(PermissionEvaluator permissionEvaluator, WorkItemStore workItemStore) {
        this.permissionEvaluator = permissionEvaluator;
        this.workItemStore = workItemStore;
    }

    public WorkItem get(long userId, long organizationId, long workItemId) {
        WorkItem item = workItemStore.findByIdAndScope(organizationId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireOrganization(
                userId, organizationId, item.type().permissionResource() + ".read");
        return item;
    }

    public WorkItemPage list(
            long userId,
            long organizationId,
            WorkItemType type,
            WorkItemStatus status,
            int page,
            int pageSize) {
        if (type == null) {
            for (String resource : new String[] {"requirement", "ux", "task"}) {
                permissionEvaluator.requireOrganization(userId, organizationId, resource + ".read");
            }
        } else {
            permissionEvaluator.requireOrganization(
                    userId, organizationId, type.permissionResource() + ".read");
        }
        return workItemStore.findPage(organizationId, type, status, page, pageSize);
    }
}
