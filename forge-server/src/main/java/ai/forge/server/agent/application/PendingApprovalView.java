package ai.forge.server.agent.application;

import java.time.Instant;

/* 待审批列表项：仅暴露审批人决策所需的最小事实，不携带任何冻结参数。 */
public record PendingApprovalView(
        /* 审批稳定标识。 */ String id,
        /* 供审批人识别的 Tool 名称。 */ String toolName,
        /* Tool 风险等级。 */ String riskLevel,
        /* 发起用户标识，前端据此提示 HIGH 不可自批。 */ long requestedBy,
        /* 发起用户界面显示名。 */ String requestedByName,
        /* 风险与影响原因。 */ String reason,
        /* 审批失效时间，UTC。 */ Instant expiresAt,
        /* 乐观锁版本，用于 decide 的 expectedVersion。 */ long version) {
}
