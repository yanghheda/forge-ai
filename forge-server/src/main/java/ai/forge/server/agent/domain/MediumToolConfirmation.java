package ai.forge.server.agent.domain;

public enum MediumToolConfirmation {

    /* 每个 MEDIUM 写操作执行前都要求用户确认；是本轮默认策略。 */
    ASK,

    /* 在权限校验通过的前提下直接执行 MEDIUM 写操作，不再逐次确认。 */
    ALLOW,

    /* 拒绝执行任何 MEDIUM 写操作，Agent 只能完成只读部分。 */
    DENY
}
