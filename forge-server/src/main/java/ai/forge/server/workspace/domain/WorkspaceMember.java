package ai.forge.server.workspace.domain;

public record WorkspaceMember(
        /* Workspace 成员关系的稳定业务标识。 */
        long id,
        /* 成员关系所属 Workspace，是租户访问范围。 */
        long workspaceId,
        /* 加入 Workspace 的实例用户标识。 */
        long userId,
        /* 用户用于成员管理界面识别的展示邮箱。 */
        String email,
        /* 用户在成员管理界面显示的名称。 */
        String displayName,
        /* 成员关系是否当前有效。 */
        boolean active) {}
