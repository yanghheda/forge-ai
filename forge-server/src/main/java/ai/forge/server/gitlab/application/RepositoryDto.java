package ai.forge.server.gitlab.application;

public record RepositoryDto(
        /* GitLab 项目的不可变远端标识。 */ String remoteProjectId,
        /* 含群组命名空间的仓库路径。 */ String pathWithNamespace,
        /* 不含凭据的 HTTPS 仓库地址。 */ String httpUrl,
        /* GitLab 当前默认分支；远端未配置时为空。 */ String defaultBranch) {}
