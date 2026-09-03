package ai.forge.server.project.domain;

import java.time.Instant;

public record Project(
        /* 项目的稳定业务标识，仅结合工作区范围用于服务端资源定位。 */
        long id,
        /* 所属工作区，用于所有项目查询的租户隔离条件。 */
        long workspaceId,
        /* 工作区内唯一的项目短键，供路由和后续编号使用。 */
        String key,
        /* 项目的界面展示名称。 */
        String name,
        /* 项目的可选业务说明；空字符串表示未填写。 */
        String description,
        /* 项目当前生命周期状态。 */
        ProjectStatus status,
        /* 归档的 UTC 时间；项目未归档时为空。 */
        Instant archivedAt,
        /* 与客户端 expectedVersion 比较的乐观锁版本。 */
        long version,
        /* 项目创建的 UTC 时间。 */
        Instant createdAt,
        /* 项目最近一次更新的 UTC 时间。 */
        Instant updatedAt) {}
