package ai.forge.server.agent.domain;

/* 内部 Tool 执行入口的协议级拒绝；携带稳定错误码与 HTTP 状态数值供内部端点直接映射。 */
public class ToolExecutionRejectedException extends RuntimeException {

    /* 协议级拒绝对应的 HTTP 状态数值，如 400、403、404、409；领域层不依赖具体框架类型。 */
    private final int status;

    /* 供 forge-agent 判断拒绝原因的稳定错误码。 */
    private final String errorCode;

    public ToolExecutionRejectedException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
