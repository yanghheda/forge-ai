package ai.forge.server.qa.application;

import ai.forge.server.qa.domain.BugSeverity;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.util.List;

public record BugView(
        /* Bug 工作项标识。 */ long id,
        /* 公司内展示编号。 */ String itemKey,
        /* Bug 标题。 */ String title,
        /* 当前缺陷状态。 */ WorkItemStatus status,
        /* 发布阻断语义使用的严重级别。 */ BugSeverity severity,
        /* 关联的 Requirement。 */ long requirementId,
        /* 来源 Test Run；人工草稿为空。 */ Long testRunId,
        /* 来源失败结果；人工草稿为空。 */ Long testResultId,
        /* 可执行复现步骤。 */ List<String> reproductionSteps,
        /* 测试期望结果。 */ String expectedResult,
        /* 实际失败结果。 */ String actualResult,
        /* 研发修复说明；未解决为空。 */ String fixNote,
        /* MR、Commit 等修复证据。 */ List<String> fixEvidence,
        /* Work Item 乐观锁版本。 */ long version) {}
