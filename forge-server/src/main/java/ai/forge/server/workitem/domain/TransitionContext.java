package ai.forge.server.workitem.domain;

public record TransitionContext(
        /* 当前转换所作用的 Requirement。 */
        WorkItem workItem,
        /* 调用方为需要审计的动作提供的原因；无需原因时为空。 */
        String reason) {}
