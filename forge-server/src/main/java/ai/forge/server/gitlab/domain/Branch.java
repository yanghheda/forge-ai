package ai.forge.server.gitlab.domain;

import java.time.LocalDateTime;

public record Branch(
        /* 本地分支快照标识；远端 GitLab 不认识该值。 */ long id,
        /* 分支所属工作区，用于所有持久化查询的租户范围。 */ long workspaceId,
        /* 分支所属本地仓库绑定标识。 */ long repositoryId,
        /* 关联的研发工作项；无关联远端分支时为空。 */ Long workItemId,
        /* GitLab 仓库内的完整分支名。 */ String name,
        /* 最近一次从远端确认的分支头提交 SHA。 */ String commitSha,
        /* 本地缓存状态；远端仍是最终事实。 */ String status,
        /* GitLab 返回的资源更新时间；未提供时使用观察时间。 */ LocalDateTime remoteUpdatedAt,
        /* ForgeAI 最近一次完成远端核对的时间。 */ LocalDateTime lastSyncedAt) {}
