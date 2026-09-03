package ai.forge.server.project.domain;

public record ProjectMember(
        /* 项目成员关系的稳定业务标识。 */
        long id,
        /* 成员关系所属工作区，用于防御性租户范围校验。 */
        long workspaceId,
        /* 成员关系所属项目标识。 */
        long projectId,
        /* 被授予项目访问范围的用户标识。 */
        long userId,
        /* 用户用于成员管理界面识别的展示邮箱。 */
        String email,
        /* 用户在成员管理界面显示的名称。 */
        String displayName,
        /* 成员关系是否当前有效。 */
        boolean active) {}
