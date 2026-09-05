package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class DevelopmentQaQuery {

    /* 在返回研发交付快照前执行 Requirement 读取授权。 */
    private final PermissionEvaluator permissions;

    /* 查询严格限定 Workspace、Project 与 Requirement 的本地投影。 */
    private final DevelopmentQaStore store;

    public DevelopmentQaQuery(PermissionEvaluator permissions, DevelopmentQaStore store) {
        this.permissions = permissions;
        this.store = store;
    }

    public DevelopmentQaSummary get(
            long userId,
            long workspaceId,
            long projectId,
            long requirementId) {
        permissions.requireProject(userId, workspaceId, projectId, "requirement.read");
        return store.summarize(workspaceId, projectId, requirementId);
    }
}
