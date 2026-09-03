package ai.forge.server.platform.web;

public enum ErrorCode {
    /* 请求指向的公开资源或路由不存在。 */
    RESOURCE_NOT_FOUND,
    /* 请求结构或字段约束不满足公开契约。 */
    VALIDATION_FAILED,
    /* 单例实例已完成首个 Owner 初始化，公开入口永久关闭。 */
    INSTANCE_ALREADY_INITIALIZED,
    /* 请求没有可验证的服务端 Session 或登录凭据无效。 */
    UNAUTHENTICATED,
    /* 当前来源与邮箱组合超过登录尝试窗口上限。 */
    LOGIN_RATE_LIMITED,
    /* 未被稳定业务错误覆盖的服务端故障。 */
    INTERNAL_ERROR
}
