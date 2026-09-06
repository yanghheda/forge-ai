package ai.forge.server.release.application;

import java.time.Instant;
import java.util.List;

public record ReleaseView(
        /* Release Candidate 标识。 */ long id,
        /* 所属工作区。 */ long workspaceId,
        /* 所属项目。 */ long projectId,
        /* 项目与环境内唯一版本名。 */ String versionName,
        /* 目标环境。 */ String environment,
        /* 当前聚合状态。 */ String status,
        /* 可编辑 Release Note。 */ String releaseNote,
        /* 纳入发布的 Requirement 标识。 */ List<Long> itemIds,
        /* 最近一次 Precheck；尚未运行时为空。 */ PrecheckSnapshot latestPrecheck,
        /* 聚合乐观锁版本。 */ long version,
        /* 创建时间。 */ Instant createdAt,
        /* 最近修改时间。 */ Instant updatedAt) {

    public ReleaseView {
        itemIds = List.copyOf(itemIds);
    }
}
