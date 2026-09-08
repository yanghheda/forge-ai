package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.Instant;

public record WorkItemSummary(
        /* 工作项稳定标识，客户端仍需结合当前 scope 使用。 */ long id,
        /* 所属工作区，用于列表结果的租户范围核对。 */ long workspaceId,
        /* 所属项目，用于列表结果的项目范围核对。 */ long projectId,
        /* 项目内单调编号，用于稳定倒序分页。 */ long itemNumber,
        /* 面向用户展示的项目内工作项键。 */ String itemKey,
        /* 决定状态与权限语义的工作项类型。 */ WorkItemType type,
        /* 列表展示使用的短标题。 */ String title,
        /* 服务端权威的当前工作流状态。 */ WorkItemStatus status,
        /* 工作项的业务处理优先级。 */ WorkItemPriority priority,
        /* 当前负责人；尚未分配时为空。 */ Long assigneeUserId,
        /* 期望完成时间；没有截止日期时为空。 */ Instant dueAt,
        /* 聚合乐观锁版本，用于后续详情或编辑校验。 */ long version,
        /* 工作项创建时间。 */ Instant createdAt,
        /* 工作项最近更新时间。 */ Instant updatedAt) {}
