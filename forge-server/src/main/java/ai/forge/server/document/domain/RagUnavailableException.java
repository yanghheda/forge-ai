package ai.forge.server.document.domain;

/** 检索依赖的派生索引暂不可用；调用方应降级而不是伪装成空结果。 */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
