package ai.forge.server.gitlab.domain;

import java.time.LocalDateTime;

public record PipelineRun(
        /* 本地 Pipeline 快照稳定标识；尚未持久化时为零。 */ long id,
        /* Pipeline 所属工作区租户范围。 */ long organizationId,
        /* Pipeline 所属仓库绑定标识。 */ long repositoryId,
        /* 可选关联 MR 本地标识；分支 Pipeline 可为空。 */ Long mergeRequestId,
        /* GitLab 分配的项目内 Pipeline 标识。 */ long remotePipelineId,
        /* Pipeline 执行所针对的分支或标签。 */ String ref,
        /* Pipeline 执行所针对的提交 SHA。 */ String commitSha,
        /* GitLab 返回的标准化执行状态。 */ String status,
        /* 不含凭据的 GitLab Pipeline 页面地址。 */ String webUrl,
        /* GitLab 记录的开始时间；尚未开始时为空。 */ LocalDateTime startedAt,
        /* GitLab 记录的结束时间；未结束时为空。 */ LocalDateTime finishedAt,
        /* GitLab 对象更新时间，用于拒绝乱序 Webhook 覆盖。 */ LocalDateTime remoteUpdatedAt,
        /* ForgeAI 最近完成远端核对的 UTC 时间。 */ LocalDateTime lastSyncedAt,
        /* UI 展示所需的有界摘要 JSON，不包含完整日志。 */ String summaryJson) {}
