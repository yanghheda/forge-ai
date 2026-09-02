package ai.forge.server.platform.web;

import java.util.Map;

public record ApiError(
        /* 供调用方稳定分支处理的机器可读错误码。 */
        ErrorCode code,
        /* 面向调用方的安全错误摘要，不包含内部堆栈。 */
        String message,
        /* 关联当前请求、响应与日志的请求标识。 */
        String requestId,
        /* 与当前错误有关的结构化补充信息；无内容时返回空对象。 */
        Map<String, Object> details) {}
