package ai.forge.server.qa.domain;

public enum TestRunStatus {
    /* 尚未执行任何用例的运行。 */ DRAFT,
    /* 已产生至少一个执行结果。 */ IN_PROGRESS,
    /* 统计已固化且结果不可直接修改。 */ COMPLETED,
    /* 不再参与 QA 放行判断的运行。 */ CANCELLED
}
