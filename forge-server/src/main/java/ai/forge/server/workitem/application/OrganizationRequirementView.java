package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.time.Instant;
import java.util.List;

public record OrganizationRequirementView(
        /* Requirement 的稳定标识。 */ long id,
        /* 单组织内由服务端生成的可读需求编号。 */ String itemKey,
        /* 需求列表与详情展示的简短标题。 */ String title,
        /* 需求的详细业务描述；为空字符串表示尚未补充。 */ String description,
        /* 服务端工作流权威状态。 */ WorkItemStatus status,
        /* 需求业务优先级。 */ WorkItemPriority priority,
        /* 当前单公司产品范围的展示名称。 */ String organizationName,
        /* 创建需求的人员展示名称。 */ String reporterName,
        /* 期望完成时间；未设置时为空。 */ Instant dueAt,
        /* 结构化产品材料中的业务目标。 */ String goal,
        /* 本次需求明确包含的范围。 */ String inScope,
        /* 本次需求明确排除的范围。 */ String outOfScope,
        /* 用于产品评审 Guard 和页面展示的验收标准。 */ List<String> acceptanceCriteria,
        /* 需求可带来的业务价值说明。 */ String businessValue,
        /* 乐观锁版本。 */ long version,
        /* 需求创建时间。 */ Instant createdAt,
        /* 需求最近更新时间。 */ Instant updatedAt) {}
