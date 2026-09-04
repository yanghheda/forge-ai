package ai.forge.server.gitlab.domain;

import java.time.LocalDateTime;

public record GitRepository(
        /* 仓库绑定的本地稳定标识。 */ long id,
        /* 仓库所属工作区。 */ long workspaceId,
        /* 仓库绑定的 ForgeAI 项目标识。 */ long projectId,
        /* 使用的 GitLab 连接标识。 */ long connectionId,
        /* GitLab 项目的不可变标识。 */ String remoteProjectId,
        /* 含群组命名空间的仓库路径。 */ String pathWithNamespace,
        /* 不含凭据的 HTTPS 仓库地址。 */ String httpUrl,
        /* 远端默认分支；未配置时为空。 */ String defaultBranch,
        /* 当前绑定状态。 */ String status,
        /* 最近读取 GitLab 事实的 UTC 时间。 */ LocalDateTime lastSyncedAt,
        /* 乐观锁版本。 */ long version) {}
