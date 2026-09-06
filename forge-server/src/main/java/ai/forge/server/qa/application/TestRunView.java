package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.QaGuardDecision;
import ai.forge.server.qa.domain.TestRunStatus;
import ai.forge.server.qa.domain.TestRunSummary;
import java.time.LocalDateTime;
import java.util.List;

public record TestRunView(
        /* Test Run 标识。 */ long id,
        /* 被验证的 Requirement 标识。 */ long requirementId,
        /* 执行环境说明。 */ String environment,
        /* Test Run 生命周期状态。 */ TestRunStatus status,
        /* 完成时固化的统计；未完成时为空。 */ TestRunSummary summary,
        /* 服务端计算的 QA 放行结论。 */ QaGuardDecision decision,
        /* 本次运行绑定的结果集合。 */ List<TestResultView> results,
        /* 运行开始时间。 */ LocalDateTime startedAt,
        /* 运行完成时间；未完成时为空。 */ LocalDateTime finishedAt,
        /* 运行乐观锁版本。 */ long version) {}
