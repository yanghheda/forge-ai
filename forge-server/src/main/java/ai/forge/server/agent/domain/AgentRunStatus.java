package ai.forge.server.agent.domain;

public enum AgentRunStatus {
    /* Run 已持久化，等待调度。 */
    QUEUED(false),
    /* Run 正在执行。 */
    RUNNING(false),
    /* Run 已保存 checkpoint，等待持久化审批决定。 */
    WAITING_APPROVAL(false),
    /* Run 已成功完成。 */
    SUCCEEDED(true),
    /* Run 因稳定错误终止。 */
    FAILED(true),
    /* Run 被显式取消。 */
    CANCELLED(true),
    /* Run 因超时失效。 */
    EXPIRED(true);

    /* 是否属于关闭 SSE 的终态。 */
    private final boolean terminal;

    AgentRunStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean terminal() {
        return terminal;
    }
}
