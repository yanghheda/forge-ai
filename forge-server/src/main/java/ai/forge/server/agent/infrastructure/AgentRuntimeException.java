package ai.forge.server.agent.infrastructure;

public class AgentRuntimeException extends RuntimeException {

    /* 可写入 Agent Run 的稳定失败码。 */
    private final String errorCode;

    /* 调用方是否可在不修改输入的情况下安全重试。 */
    private final boolean retryable;

    public AgentRuntimeException(
            String errorCode, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
