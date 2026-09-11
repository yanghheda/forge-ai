package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.WorkItemStatus;
import java.time.Instant;

public record DevelopmentQaTask(
        /* Dev Task 稳定标识。 */ long id,
        /* 公司内可读任务编号。 */ String itemKey,
        /* 开发页面展示的任务标题。 */ String title,
        /* Dev Task 当前状态。 */ WorkItemStatus status,
        /* 完成任务写入使用的乐观锁版本。 */ long version,
        /* 可选关联分支名称。 */ String branchName,
        /* 可选关联分支头提交。 */ String branchCommitSha,
        /* 可选关联 MR 的本地稳定标识。 */ Long mergeRequestId,
        /* 可选且不含凭据的 MR 页面地址。 */ String mergeRequestUrl,
        /* 当前 MR head SHA；CI 必须验证该提交。 */ String mergeRequestHeadSha,
        /* 最新 Pipeline 的本地稳定标识。 */ Long pipelineId,
        /* 最新 Pipeline 实际验证的提交 SHA。 */ String pipelineCommitSha,
        /* 最新 Pipeline 的标准化状态。 */ String pipelineStatus,
        /* Pipeline 最近同步时间；用于提示快照新鲜度。 */ Instant pipelineLastSyncedAt) {}
