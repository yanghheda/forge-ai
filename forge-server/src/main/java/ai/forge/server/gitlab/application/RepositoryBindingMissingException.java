package ai.forge.server.gitlab.application;

/**
 * 需求未绑定可用的 GitLab 仓库（连接、凭据或仓库快照缺失）时启动研发的稳定业务错误。
 * 与普通的“资源不存在”区分开，避免前端把缺少集成配置误报成“无权访问”。
 */
public class RepositoryBindingMissingException extends RuntimeException {

    public RepositoryBindingMissingException() {
        super("The requirement has no active GitLab repository binding");
    }
}
