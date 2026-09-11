package ai.forge.server.organization.domain;

public record OrganizationContext(
        /* 当前实例唯一公司的稳定标识。 */ long organizationId,
        /* 顶部导航与页面标题展示的公司名称。 */ String organizationName,
        /* 当前用户是否拥有公司全部流程与管理权限。 */ boolean owner) {}
