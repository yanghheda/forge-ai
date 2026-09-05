package ai.forge.server.platform.web;

public enum ErrorCode {
    /* 请求指向的公开资源或路由不存在。 */
    RESOURCE_NOT_FOUND,
    /* 同一 Workspace 内项目短键已经被其他项目占用。 */
    PROJECT_KEY_CONFLICT,
    /* 创建成员账号时邮箱已经被现有本地账号占用。 */
    MEMBER_EMAIL_CONFLICT,
    /* 写入请求携带的 expectedVersion 已落后于当前资源版本。 */
    VERSION_CONFLICT,
    /* 当前状态或工作项类型不允许执行请求中的固定 Action。 */
    INVALID_TRANSITION,
    /* 状态转换所需的确定性业务材料尚未满足。 */
    WORKFLOW_GUARD_FAILED,
    /* 同一 Work Item 的幂等键已被不同 Action 使用。 */
    IDEMPOTENCY_CONFLICT,
    /* 相同方向与类型的 Work Item 关系已经存在。 */
    RELATION_CONFLICT,
    /* Agent Run 幂等键被不同输入重用。 */
    AGENT_RUN_IDEMPOTENCY_CONFLICT,
    /* 请求结构或字段约束不满足公开契约。 */
    VALIDATION_FAILED,
    /* 单例实例已完成首个 Owner 初始化，公开入口永久关闭。 */
    INSTANCE_ALREADY_INITIALIZED,
    /* 请求没有可验证的服务端 Session 或登录凭据无效。 */
    UNAUTHENTICATED,
    /* 当前来源与邮箱组合超过登录尝试窗口上限。 */
    LOGIN_RATE_LIMITED,
    /* 修改请求缺少有效的服务端 Session CSRF Token。 */
    CSRF_REJECTED,
    /* 修改请求的浏览器来源不在当前实例允许列表。 */
    ORIGIN_REJECTED,
    /* 检索依赖的派生向量索引暂不可用；文档事实本身仍可读取。 */
    RAG_UNAVAILABLE,
    /* GitLab Token 未通过远端认证。 */
    GITLAB_UNAUTHORIZED,
    /* GitLab Token 缺少当前读取操作权限。 */
    GITLAB_FORBIDDEN,
    /* GitLab 远端资源不存在。 */
    GITLAB_NOT_FOUND,
    /* GitLab 请求超过远端速率限制。 */
    GITLAB_RATE_LIMITED,
    /* GitLab 远端资源状态冲突。 */
    GITLAB_CONFLICT,
    /* GitLab 请求超过本地安全超时。 */
    GITLAB_TIMEOUT,
    /* GitLab 服务或网络当前不可用。 */
    GITLAB_UNAVAILABLE,
    /* GitLab 返回重定向、超限或不符合契约的响应。 */
    GITLAB_INVALID_RESPONSE,
    /* 同名远端 Branch 已指向不同基准 SHA，禁止覆盖或错误复用。 */
    REMOTE_RESOURCE_CONFLICT,
    /* Requirement 或 Dev Task 当前状态不允许启动研发。 */
    DEVELOPMENT_STATE_CONFLICT,
    /* 未被稳定业务错误覆盖的服务端故障。 */
    INTERNAL_ERROR
}
