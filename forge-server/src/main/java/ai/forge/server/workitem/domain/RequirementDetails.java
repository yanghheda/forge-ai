package ai.forge.server.workitem.domain;

import java.time.Instant;
import java.util.List;

public record RequirementDetails(
        /* 对应的 Requirement 工作项标识。 */ long workItemId,
        /* 所属工作区范围。 */ long workspaceId,
        /* 可验证的业务目标。 */ String goal,
        /* 本次明确纳入的范围。 */ String inScope,
        /* 本次明确排除的范围；允许为空。 */ String outOfScope,
        /* 可逐条验收的标准。 */ List<String> acceptanceCriteria,
        /* 可选业务价值说明。 */ String businessValue,
        /* 独立于 Work Item 的材料乐观锁版本。 */ long version,
        /* 材料最近更新时间。 */ Instant updatedAt) {}
