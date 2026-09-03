package ai.forge.server.workitem.domain;

import java.time.Instant;

public record WorkItem(
        /* 工作项的稳定业务标识，必须结合租户和项目范围定位。 */
        long id,
        /* 所属工作区，用于服务端租户隔离查询。 */
        long workspaceId,
        /* 所属项目，用于授权、编号和列表范围。 */
        long projectId,
        /* 项目内单调分配且逻辑删除后不复用的数值编号。 */
        long itemNumber,
        /* 由服务端项目短键和数值编号组成的展示标识。 */
        String itemKey,
        /* 决定初始状态与权限映射的工作项类型。 */
        WorkItemType type,
        /* 工作项的简短展示标题。 */
        String title,
        /* 工作项的详细业务说明；空字符串表示未填写。 */
        String description,
        /* 由类型和后续固定状态机控制的当前状态。 */
        WorkItemStatus status,
        /* 工作项在项目范围内的处理优先级。 */
        WorkItemPriority priority,
        /* 当前负责人用户标识；未分配时为空。 */
        Long assigneeUserId,
        /* 创建工作项的会话用户标识。 */
        long reporterUserId,
        /* 期望完成的 UTC 时间；没有截止日期时为空。 */
        Instant dueAt,
        /* 与 PATCH expectedVersion 比较的乐观锁版本。 */
        long version,
        /* 工作项创建的 UTC 时间。 */
        Instant createdAt,
        /* 工作项最近一次基础字段更新的 UTC 时间。 */
        Instant updatedAt) {}
