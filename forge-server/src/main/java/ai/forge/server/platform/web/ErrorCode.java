package ai.forge.server.platform.web;

public enum ErrorCode {
    /* 请求指向的公开资源或路由不存在。 */
    RESOURCE_NOT_FOUND,
    /* 请求结构或字段约束不满足公开契约。 */
    VALIDATION_FAILED,
    /* 未被稳定业务错误覆盖的服务端故障。 */
    INTERNAL_ERROR
}
