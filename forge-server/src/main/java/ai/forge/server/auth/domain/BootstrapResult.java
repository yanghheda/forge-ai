package ai.forge.server.auth.domain;

public record BootstrapResult(
        /* 初始化创建的首个用户标识。 */
        long userId,
        /* 初始化创建的默认组织标识。 */
        long organizationId,
        /* 初始化创建的默认工作区标识。 */
        long workspaceId,
        /* 可在后续界面和 API 路径中使用的组织短名。 */
        String organizationSlug,
        /* 可在后续界面和 API 路径中使用的工作区短名。 */
        String workspaceSlug) {}
