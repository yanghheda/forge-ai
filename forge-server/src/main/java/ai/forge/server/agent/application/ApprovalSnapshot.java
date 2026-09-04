package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.ApprovalStatus;
import java.time.Instant;
import java.util.List;

public record ApprovalSnapshot(
        /* 审批稳定标识。 */ String id,
        /* 关联 Agent Run。 */ String runId,
        /* 原始 Tool Call 标识。 */ String toolCallId,
        /* 供审批人识别的 Tool 名称。 */ String toolName,
        /* 冻结契约版本。 */ int toolVersion,
        /* Tool 风险等级。 */ String riskLevel,
        /* 当前审批状态。 */ ApprovalStatus status,
        /* 发起用户，禁止其自批。 */ long requestedBy,
        /* 已决策用户；待审批时为空。 */ Long approverUserId,
        /* 仅展示摘要，不返回完整冻结参数。 */ String argumentHash,
        /* 受影响资源版本快照。 */ List<ResourceVersion> resources,
        /* 风险与影响原因。 */ String reason,
        /* 审批失效时间。 */ Instant expiresAt,
        /* 乐观锁版本。 */ long version) {

    public record ResourceVersion(
            /* 资源类型。 */ String type,
            /* 资源标识。 */ String id,
            /* 审批冻结时的资源版本。 */ long version) {
    }
}
