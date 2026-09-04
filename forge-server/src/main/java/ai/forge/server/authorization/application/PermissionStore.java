package ai.forge.server.authorization.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionStore {

    Set<String> findWorkspacePermissionSet(long userId, long workspaceId);

    Optional<ProjectAccess> findProjectAccess(long userId, long workspaceId, long projectId);

    /* 列出用户在指定工作区内对该权限有效的全部项目标识；用于派生强制检索范围。 */
    List<Long> findProjectIdsWithPermission(long userId, long workspaceId, String permission);

    record ProjectAccess(
            /* 当前成员是否具有该项目的有效访问范围。 */
            boolean projectMember,
            /* Workspace 与指定 Project 范围合并后的角色代码。 */
            Set<String> roleCodes,
            /* Workspace 与指定 Project 范围合并后的权限代码。 */
            Set<String> permissions) {}
}
