package ai.forge.server.qa.domain;

public enum TestCaseType {
    /* 验证单项业务功能的测试用例。 */
    FUNCTIONAL,
    /* 验证既有能力未被变更破坏的回归用例。 */
    REGRESSION,
    /* 验证跨组件完整业务链路的端到端用例。 */
    E2E
}
