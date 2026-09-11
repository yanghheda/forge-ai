package ai.forge.server.gitlab.domain;

import java.time.LocalDateTime;

public record MergeRequest(
        /* 本地 MR 快照标识。 */ long id,
        /* MR 所属工作区租户范围。 */ long organizationId,
        /* MR 所属本地仓库绑定。 */ long repositoryId,
        /* 关联的研发工作项；未关联时为空。 */ Long workItemId,
        /* GitLab 在项目内分配的 MR IID。 */ long remoteMrIid,
        /* 最近一次同步的 MR 标题。 */ String title,
        /* MR 源分支。 */ String sourceBranch,
        /* MR 目标分支。 */ String targetBranch,
        /* GitLab 标准化状态快照。 */ String state,
        /* 不含凭据的远端 MR 页面地址。 */ String webUrl,
        /* GitLab 作者外部标识；未知时为空。 */ String authorExternalId,
        /* 最近一次同步的源分支头 SHA。 */ String headSha,
        /* GitLab 合并检查状态快照。 */ String mergeStatus,
        /* GitLab 返回的资源更新时间。 */ LocalDateTime remoteUpdatedAt,
        /* ForgeAI 最近一次完成远端核对的时间。 */ LocalDateTime lastSyncedAt,
        /* 本地关联信息的乐观锁版本。 */ long version) {}
