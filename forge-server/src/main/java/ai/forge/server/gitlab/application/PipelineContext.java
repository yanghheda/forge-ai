package ai.forge.server.gitlab.application;

public record PipelineContext(
        /* 请求所属公司范围。 */ long organizationId,
        /* 本地仓库绑定标识。 */ long repositoryId,
        /* GitLab 连接标识。 */ long connectionId,
        /* 已通过 SSRF 策略保存的 GitLab Base URL。 */ String baseUrl,
        /* GitLab 远端项目标识。 */ String remoteProjectId,
        /* 仅用于本次远端调用的临时 Token 明文。 */ String token) {}
