package ai.forge.server.gitlab.application;

public record ConnectionTestResult(
        /* GitLab 返回的当前 Token 身份数字标识。 */ String externalUserId,
        /* GitLab 返回的当前 Token 身份用户名。 */ String username) {}
