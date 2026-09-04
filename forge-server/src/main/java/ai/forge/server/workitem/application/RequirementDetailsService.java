package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.workitem.domain.RequirementDetails;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class RequirementDetailsService {

    /* 校验 Requirement 的读取和编辑权限。 */
    private final PermissionEvaluator permissions;
    /* 查询真实工作项范围。 */
    private final WorkItemStore workItems;
    /* 读写带租户范围的结构化材料。 */
    private final RequirementDetailsStore store;

    public RequirementDetailsService(
            PermissionEvaluator permissions, WorkItemStore workItems, RequirementDetailsStore store) {
        this.permissions = permissions;
        this.workItems = workItems;
        this.store = store;
    }

    public RequirementDetails get(long userId, long workspaceId, long projectId, long workItemId) {
        requireRequirement(workspaceId, projectId, workItemId);
        permissions.requireProject(userId, workspaceId, projectId, "requirement.read");
        return store.find(workspaceId, projectId, workItemId)
                .orElse(new RequirementDetails(workItemId, workspaceId, "", "", "", List.of(), "", 0, null));
    }

    @Transactional
    public RequirementDetails save(
            long userId, long workspaceId, long projectId, long workItemId,
            String goal, String inScope, String outOfScope, List<String> acceptanceCriteria,
            String businessValue, long expectedVersion) {
        requireRequirement(workspaceId, projectId, workItemId);
        permissions.requireProject(userId, workspaceId, projectId, "requirement.edit");
        List<String> criteria = acceptanceCriteria.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
        boolean changed = store.save(workspaceId, projectId, workItemId, normalize(goal), normalize(inScope),
                normalize(outOfScope), criteria, normalize(businessValue), expectedVersion);
        if (!changed) {
            throw new VersionConflictException();
        }
        return store.find(workspaceId, projectId, workItemId).orElseThrow();
    }

    private void requireRequirement(long workspaceId, long projectId, long workItemId) {
        if (workItems.findByIdAndScope(workspaceId, projectId, workItemId)
                .filter(item -> item.type() == WorkItemType.REQUIREMENT).isEmpty()) {
            throw new ResourceNotFoundException();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
