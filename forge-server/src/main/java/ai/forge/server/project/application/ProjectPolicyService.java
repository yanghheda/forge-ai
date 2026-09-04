package ai.forge.server.project.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.project.domain.ProjectPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class ProjectPolicyService {

    /* 确认 Project 属于请求 Workspace。 */
    private final ProjectStore projectStore;

    /* 强制 project.manage 最终授权。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 读写带租户 scope 和乐观锁的策略事实。 */
    private final ProjectPolicyStore policyStore;

    public ProjectPolicyService(
            ProjectStore projectStore,
            PermissionEvaluator permissionEvaluator,
            ProjectPolicyStore policyStore) {
        this.projectStore = projectStore;
        this.permissionEvaluator = permissionEvaluator;
        this.policyStore = policyStore;
    }

    public ProjectPolicy get(long userId, long workspaceId, long projectId) {
        requireProject(userId, workspaceId, projectId);
        return policyStore.find(workspaceId, projectId).orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public ProjectPolicy update(
            long userId,
            long workspaceId,
            long projectId,
            boolean allowSkipUx,
            long expectedVersion) {
        requireProject(userId, workspaceId, projectId);
        if (!policyStore.update(workspaceId, projectId, allowSkipUx, userId, expectedVersion)) {
            throw new VersionConflictException();
        }
        return get(userId, workspaceId, projectId);
    }

    private void requireProject(long userId, long workspaceId, long projectId) {
        projectStore.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireProject(userId, workspaceId, projectId, "project.manage");
    }

}
