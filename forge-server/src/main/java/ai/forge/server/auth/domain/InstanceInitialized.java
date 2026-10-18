package ai.forge.server.auth.domain;

/** 实例完成首次初始化的事实，供演示环境按需准备默认集成配置。 */
public record InstanceInitialized(
        /* 初始化创建的默认公司标识。 */
        long organizationId) {}
