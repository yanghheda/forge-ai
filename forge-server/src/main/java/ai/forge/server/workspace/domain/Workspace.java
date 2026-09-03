package ai.forge.server.workspace.domain;

public record Workspace(
        /* Workspace 的稳定业务标识，用作租户范围主键。 */
        long id,
        /* 承载该 Workspace 的组织标识。 */
        long organizationId,
        /* Workspace 的界面展示名称。 */
        String name,
        /* 组织内唯一且用于 Web 路由的短名。 */
        String slug,
        /* Workspace 是否处于可访问状态。 */
        boolean active) {}
