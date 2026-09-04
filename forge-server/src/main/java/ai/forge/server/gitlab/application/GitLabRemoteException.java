package ai.forge.server.gitlab.application;

public final class GitLabRemoteException extends RuntimeException {

    /* 可稳定映射到 API 的 GitLab 失败分类，不包含远端响应正文。 */
    private final String code;

    public GitLabRemoteException(String code) {
        super("GitLab request failed: " + code);
        this.code = code;
    }

    public GitLabRemoteException(String code, Throwable cause) {
        super("GitLab request failed: " + code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
