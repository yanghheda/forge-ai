package ai.forge.server.release.application;

import java.time.Instant;

public record DeploymentView(
        /* 部署记录标识。 */ long id,
        /* 所属工作区。 */ long workspaceId,
        /* 所属项目。 */ long projectId,
        /* 被部署的 Release Candidate。 */ long releaseId,
        /* MVP 固定为 SIMULATED。 */ String mode,
        /* 审批与执行状态。 */ String status,
        /* 发起用户。 */ long requestedBy,
        /* 审批用户；未决策时为空。 */ Long approverUserId,
        /* 审批过期时间。 */ Instant approvalExpiresAt,
        /* 冻结的 Release 版本。 */ long releaseVersion,
        /* 冻结的 Precheck 快照。 */ long precheckId,
        /* 参数摘要。 */ String argumentHash,
        /* 是否让确定性模拟器走失败分支。 */ boolean simulateFailure,
        /* 模拟器结果码；未执行时为空。 */ String resultCode,
        /* 明确标记模拟性质的摘要；未执行时为空。 */ String resultSummary,
        /* 审批来源。 */ String approvalSource,
        /* 关联 Agent 审批；人工入口为空。 */ String agentApprovalId,
        /* 记录乐观锁版本。 */ long version,
        /* 创建时间。 */ Instant createdAt,
        /* 开始时间；未执行时为空。 */ Instant startedAt,
        /* 结束时间；未完成时为空。 */ Instant finishedAt) {}
