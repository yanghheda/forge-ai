package ai.forge.server.workspace.domain;

public record OrganizationScope(
        /* 当前实例唯一对用户可见的组织标识。 */ long organizationId,
        /* 顶部导航与页面标题展示的组织名称。 */ String organizationName,
        /* 仅供服务端兼容既有授权与租户 SQL 的工作区标识。 */ long workspaceId,
        /* 仅供服务端兼容既有交付链路与编号 SQL 的项目标识。 */ long projectId,
        /* 当前用户是否拥有组织全部流程与管理权限。 */ boolean owner) {}
