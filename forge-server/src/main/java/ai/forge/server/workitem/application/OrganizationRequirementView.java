package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.time.Instant;

public record OrganizationRequirementView(
        /* Requirement 的稳定标识。 */ long id,
        /* 单组织内由服务端生成的可读需求编号。 */ String itemKey,
        /* 需求列表与详情展示的简短标题。 */ String title,
        /* 需求的详细业务描述；为空字符串表示尚未补充。 */ String description,
        /* 服务端工作流权威状态。 */ WorkItemStatus status,
        /* 需求业务优先级。 */ WorkItemPriority priority,
        /* 乐观锁版本。 */ long version,
        /* 需求创建时间。 */ Instant createdAt,
        /* 需求最近更新时间。 */ Instant updatedAt) {}
