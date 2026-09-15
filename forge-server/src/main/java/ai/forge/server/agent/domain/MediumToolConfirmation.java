package ai.forge.server.agent.domain;

public enum MediumToolConfirmation {

    /* 兼容显式请求逐项确认的旧入口；常规流程不再采用。 */
    ASK,

    /* 在权限校验通过的前提下直接执行 MEDIUM 写操作；是常规流程默认策略。 */
    ALLOW,

    /* 拒绝执行任何 MEDIUM 写操作，Agent 只能完成只读部分。 */
    DENY
}
