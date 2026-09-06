package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import java.time.LocalDateTime;
import java.util.List;

public record TestResultView(
        /* Test Result 标识。 */ long id,
        /* 对应 Test Case 标识。 */ long testCaseId,
        /* Run 创建时固化引用的用例标题。 */ String title,
        /* 用例优先级。 */ TestCasePriority priority,
        /* 当前执行结论。 */ TestResultStatus status,
        /* 实际观察结果。 */ String actualResult,
        /* 脱敏证据引用。 */ List<String> evidence,
        /* 最近执行用户；未执行时为空。 */ Long executedBy,
        /* 最近执行时间；未执行时为空。 */ LocalDateTime executedAt,
        /* 结果乐观锁版本。 */ long version) {}
