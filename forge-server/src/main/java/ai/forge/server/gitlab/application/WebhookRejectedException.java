package ai.forge.server.gitlab.application;

public class WebhookRejectedException extends RuntimeException {

    /* 对外稳定 HTTP 状态；避免把 Secret 校验细节暴露给调用方。 */
    private final int status;

    public WebhookRejectedException(int status) {
        super("webhook rejected");
        this.status = status;
    }

    public int status() {
        return status;
    }
}
