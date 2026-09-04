package ai.forge.server.document.domain;

import java.time.Instant;

/** 文档元数据和当前可读版本指针。 */
public record Document(
        /* 文档稳定标识。 */ long id,
        /* 所属工作区。 */ long workspaceId,
        /* 所属项目。 */ long projectId,
        /* 可选关联的 Requirement 标识。 */ Long workItemId,
        /* 文档类型。 */ String type,
        /* 显示标题。 */ String title,
        /* 生命周期状态。 */ String status,
        /* 当前版本标识；无已保存版本时为空。 */ Long currentVersionId,
        /* 乐观锁版本。 */ long version,
        /* 创建时间。 */ Instant createdAt,
        /* 最近更新时间。 */ Instant updatedAt) {}
