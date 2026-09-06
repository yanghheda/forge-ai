package ai.forge.server.gitlab.application;

public record PendingDevelopmentOperation(
        /* 操作所属 Workspace；恢复查询与写入必须携带。 */ long workspaceId,
        /* 操作所属 Project；防止跨项目复用幂等键。 */ long projectId,
        /* 待恢复的 Dev Task。 */ long workItemId,
        /* 原始请求冻结的目标分支。 */ String targetBranch,
        /* 原始幂等键；所有恢复尝试必须复用。 */ String idempotencyKey,
        /* 已执行的后台恢复次数。 */ int attemptCount) {}
