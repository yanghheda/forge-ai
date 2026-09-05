package ai.forge.server.gitlab.application;

import java.time.LocalDateTime;

public sealed interface WebhookChange permits WebhookChange.MergeRequestChanged, WebhookChange.PipelineChanged {

    record MergeRequestChanged(
            /* GitLab 远端项目标识。 */ String remoteProjectId,
            /* GitLab 项目内 MR IID。 */ long remoteMrIid,
            /* MR 标题。 */ String title,
            /* MR 源分支。 */ String sourceBranch,
            /* MR 目标分支。 */ String targetBranch,
            /* MR 远端状态。 */ String state,
            /* MR 页面地址。 */ String webUrl,
            /* MR 作者外部标识；缺失时为空。 */ String authorExternalId,
            /* MR 头提交 SHA；缺失时为空。 */ String headSha,
            /* MR 合并检查状态；缺失时为空。 */ String mergeStatus,
            /* GitLab 对象更新时间，用于拒绝乱序覆盖。 */ LocalDateTime remoteUpdatedAt) implements WebhookChange {}

    record PipelineChanged(
            /* GitLab 远端项目标识。 */ String remoteProjectId,
            /* GitLab 远端 Pipeline 标识。 */ long remotePipelineId,
            /* Pipeline 执行引用。 */ String ref,
            /* Pipeline 提交 SHA。 */ String commitSha,
            /* Pipeline 远端状态。 */ String status,
            /* Pipeline 页面地址。 */ String webUrl,
            /* Pipeline 开始时间；缺失时为空。 */ LocalDateTime startedAt,
            /* Pipeline 结束时间；缺失时为空。 */ LocalDateTime finishedAt,
            /* GitLab 对象更新时间，用于拒绝乱序覆盖。 */ LocalDateTime remoteUpdatedAt) implements WebhookChange {}
}
