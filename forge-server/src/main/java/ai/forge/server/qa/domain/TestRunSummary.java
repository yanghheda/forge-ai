package ai.forge.server.qa.domain;

public record TestRunSummary(
        /* 本次运行纳入的用例总数。 */ int total,
        /* 执行结论为通过的用例数。 */ int passed,
        /* 执行结论为失败的用例数。 */ int failed,
        /* 因环境或依赖阻塞的用例数。 */ int blocked,
        /* 经人工明确跳过的用例数。 */ int skipped,
        /* 尚未产生执行结论的用例数。 */ int notRun,
        /* P0 或 P1 用例中被跳过的数量。 */ int mandatorySkipped) {}
