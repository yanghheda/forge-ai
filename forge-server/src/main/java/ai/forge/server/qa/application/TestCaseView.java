package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestCaseType;
import java.util.List;

public record TestCaseView(
        /* 测试用例标识。 */ long id,
        /* 公司内稳定展示编号。 */ String caseKey,
        /* 用例覆盖的 Requirement 标识。 */ long requirementId,
        /* 可读用例标题。 */ String title,
        /* 执行前置条件。 */ String preconditions,
        /* 有序执行步骤。 */ List<String> steps,
        /* 预期结果。 */ String expectedResult,
        /* QA 执行优先级。 */ TestCasePriority priority,
        /* 用例覆盖范围类型。 */ TestCaseType caseType,
        /* 创建用例的用户标识。 */ long createdBy,
        /* 创建人的界面展示名称。 */ String createdByName,
        /* 用例设计乐观锁版本。 */ long version) {}
