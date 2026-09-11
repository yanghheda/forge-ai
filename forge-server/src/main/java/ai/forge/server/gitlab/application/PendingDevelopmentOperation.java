package ai.forge.server.gitlab.application;

public record PendingDevelopmentOperation(
        /* 操作所属公司；恢复查询与写入必须携带。 */ long organizationId,
        /* 待恢复的 Dev Task。 */ long workItemId,
        /* 原始请求冻结的目标分支。 */ String targetBranch,
        /* 原始幂等键；所有恢复尝试必须复用。 */ String idempotencyKey,
        /* 已执行的后台恢复次数。 */ int attemptCount) {}
