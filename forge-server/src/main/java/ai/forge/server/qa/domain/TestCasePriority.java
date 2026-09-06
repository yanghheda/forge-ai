package ai.forge.server.qa.domain;

public enum TestCasePriority {
    /* 发布阻断级核心路径，必须执行并通过。 */ P0,
    /* 重要业务路径，必须执行并通过。 */ P1,
    /* 次要场景，项目默认策略允许明确跳过。 */ P2
}
