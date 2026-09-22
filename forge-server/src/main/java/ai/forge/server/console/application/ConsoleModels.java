package ai.forge.server.console.application;

import java.time.Instant;

public final class ConsoleModels {
    private ConsoleModels() {}

    public record SearchResult(
            /* 搜索结果的资源类型。 */ String type,
            /* 搜索结果的稳定资源标识。 */ String id,
            /* 搜索结果主标题。 */ String title,
            /* 用于快速识别资源的摘要。 */ String subtitle) {}

    public record NotificationView(
            /* 通知标识。 */ long id,
            /* 通知分类。 */ String type,
            /* 通知标题。 */ String title,
            /* 通知摘要。 */ String body,
            /* 可选关联资源类型。 */ String resourceType,
            /* 可选关联资源标识。 */ String resourceId,
            /* 首次已读时间；未读时为空。 */ Instant readAt,
            /* 通知创建时间。 */ Instant createdAt) {}

    public record BoardItem(
            /* 工作项标识。 */ long id,
            /* 公司内展示编号。 */ String itemKey,
            /* 工作项类型。 */ String type,
            /* 工作项标题。 */ String title,
            /* 看板泳道。 */ String lane,
            /* 泳道内排序位置。 */ long position,
            /* 负责人标识；未分配时为空。 */ Long assigneeUserId,
            /* 任务真实状态的乐观锁版本。 */ long version,
            /* 任务所属需求，用于打开对应阶段工作区。 */ Long requirementId) {}
}
