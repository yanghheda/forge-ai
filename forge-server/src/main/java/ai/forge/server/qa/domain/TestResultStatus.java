package ai.forge.server.qa.domain;

public enum TestResultStatus {
    /* 尚未产生执行结论。 */ NOT_RUN,
    /* 人工执行已经开始但尚未形成最终结论。 */ IN_PROGRESS,
    /* 实际结果符合预期。 */ PASS,
    /* 实际结果不符合预期。 */ FAIL,
    /* 环境或外部依赖导致无法执行。 */ BLOCKED,
    /* QA 明确决定不执行当前用例。 */ SKIPPED
}
