package ai.forge.server.document.application;

/** 向量索引暂不可用或返回不可接受响应时抛出；携带稳定错误码供重试与降级使用。 */
public class VectorIndexUnavailableException extends RuntimeException {

    /* 稳定错误码；索引重试与检索降级都依据它分类处理。 */
    private final String errorCode;

    public VectorIndexUnavailableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VectorIndexUnavailableException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
