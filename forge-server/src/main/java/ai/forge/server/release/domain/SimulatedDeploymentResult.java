package ai.forge.server.release.domain;

public record SimulatedDeploymentResult(
        /* 模拟器是否完成预设发布。 */ boolean succeeded,
        /* 供 UI、审计和 Trace 解释的稳定结果码。 */ String code,
        /* 明确声明非生产执行的结果摘要。 */ String summary) {}
