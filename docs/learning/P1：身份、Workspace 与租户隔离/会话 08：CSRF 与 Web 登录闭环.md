# 会话 08：CSRF 与 Web 登录闭环

## 我完成了什么

- `forge-server` 提供 `/api/v1/auth/csrf`，使用服务端 Session 保存 CSRF Token；所有非安全方法同时校验请求 Origin 和 `X-CSRF-TOKEN`。
- 登录、初始化、退出等写请求统一进入同一条 CSRF 防护链，并返回稳定的 JSON 错误码，不再由框架输出 HTML 错误页。
- `forge-web` 完成初始化状态探测、初始化管理员、登录、读取当前身份、受保护 Workspace 页面和退出闭环。
- Web 请求层集中获取与缓存 CSRF Token；Token 失效时只重取并重放一次，普通 403 不会被误判为 CSRF 问题。
- OpenAPI 中的 Cookie 名称与真实的 `FORGE_SESSION` 对齐，避免契约文档引导调用方使用错误 Cookie。

## 我理解的核心设计

### 双重边界：Origin 与 CSRF Token

- Origin 校验先执行。所有 `POST`、`PUT`、`PATCH`、`DELETE` 请求必须携带 `Origin`，且只能命中显式 allowlist；缺失或跨站 Origin 返回 `ORIGIN_REJECTED`。
- Origin 通过后，Spring Security 的 `CsrfFilter` 再比较 Session 中的期望 Token 与 `X-CSRF-TOKEN` 请求头；缺失、错误或属于另一 Session 的 Token 返回 `CSRF_REJECTED`。
- 两层检查解决的问题不同：Origin 限制可信前端来源，CSRF Token 证明发起写操作的页面已与当前服务端 Session 建立交互。两者不能互相替代。
- `GET`、`HEAD`、`OPTIONS` 是安全方法，不要求 CSRF Token，也不能产生业务写入副作用。

### 后端调用链

1. 浏览器先请求 `GET /api/v1/auth/csrf`。
2. Spring Session 创建或复用匿名 Session，`CsrfTokenRepository` 将 Token 与该 Session 关联。
3. Controller 返回 Token 值及应使用的 Header 名称，浏览器同时持有 `FORGE_SESSION` Cookie。
4. 浏览器发起登录、初始化或退出请求时，自动携带 Cookie、`Origin` 和 `X-CSRF-TOKEN`。
5. `OriginValidationFilter` 先校验来源，`CsrfFilter` 再校验 Token；只有两者通过，请求才进入 Controller 和应用服务。
6. 登录成功后 Spring Session 轮换 Session ID，但认证与 CSRF 状态继续受服务端 Session 管理；退出成功后 Web 清空本地 Token 缓存。

### Web 登录闭环

1. `/login` 读取 `/api/v1/setup/status`，判断实例是否已经初始化。
2. 未初始化时显示管理员初始化表单；已初始化时显示登录表单。
3. 写请求统一经 `apiRequest`：先确保存在 CSRF Token，再注入防护 Header。
4. 登录成功后进入 `/w/{workspaceSlug}`；受保护布局通过 `/api/v1/me` 重新取得用户、Workspace 和角色事实。
5. `/me` 返回 401 时回到登录页；URL 中的 Workspace 与当前身份不一致时拒绝展示该 Workspace。
6. 退出调用服务端 API 撤销 Session，随后清理 Web 端 Token，并返回登录页。

## 数据与事务边界

- CSRF Token 和认证 Session 都属于 Redis 中的短期状态，可丢失且不作为业务事实来源；Redis 丢失后用户需要重新获取 Token 或重新登录。
- 初始化状态来自 MySQL，但 Controller 只通过应用服务和 Store 查询，不直接访问 Mapper 或数据库。
- Origin 与 CSRF 校验发生在进入业务 Controller 之前，因此被拒绝的请求不会开启业务事务，也不会产生初始化、登录审计或退出等业务副作用。
- 获取 CSRF Token 只创建或更新 Session 状态，不写 MySQL；读取初始化状态与 `/me` 不应产生业务写入。
- Web 只调用 `forge-server` HTTP API，不直接连接 Redis、MySQL 或 Agent；浏览器路径中的 Workspace 不能替代服务端身份和权限判断。

## 关键实现取舍

- allowlist 是精确匹配而不是宽泛通配。生产默认值保持收紧，开发和测试环境显式允许各自的本地域名。
- 安全过滤器使用统一错误写入器输出 `ApiError`，保留 `requestId`，使前端可按错误码处理且日志可以关联。
- Web 层只对明确的 `CSRF_REJECTED` 自动恢复一次：清除旧 Token、重新获取、重放原请求。设置一次上限可以避免配置错误或服务端故障导致无限循环。
- 并发写请求共享同一个 CSRF Token 获取 Promise，避免每个请求都额外创建 Token 获取请求。
- 受保护页面重新调用 `/me`，不把登录响应或 URL 参数当成长期权限快照。

## 失败路径与风险

- 缺少或不可信 Origin：返回 403 `ORIGIN_REJECTED`，请求不会进入业务层。
- 缺少、错误或过期 CSRF Token：返回 403 `CSRF_REJECTED`；Web 最多自动恢复一次。
- 普通权限 403：原样返回，不重取 Token，避免隐藏真正的授权错误。
- Session 已过期或被退出撤销：`/me` 返回 401，Web 跳转登录页；旧的本地 Token 即使存在也不能恢复认证。
- 部署时若未把真实 Web Origin 加入 allowlist，所有写请求都会 fail closed。反向代理还必须保留正确的 Origin 和 Cookie 语义。
- Cookie 防护仍依赖部署环境正确配置 Secure、SameSite、HTTPS 和可信代理；CSRF 机制不能修复错误的 Cookie 或代理配置。
- Workspace URL 校验只是 Web 体验边界，不能替代会话 09–10 的服务端 Workspace 成员关系与 RBAC 授权。

## 测试证据

- `CsrfIntegrationTest` 使用真实 MySQL、Redis 和完整过滤器链，覆盖正确 Origin 与 Token、缺失或错误 Token、缺失或跨站 Origin，以及安全 GET 不产生业务副作用。
- 既有初始化、认证和 Session 过期集成测试改为通过真实 CSRF 获取流程发起写请求，没有关闭过滤器或使用 mock 绕过核心规则。
- Web 请求层测试覆盖安全方法、Token 注入、并发获取合并、`CSRF_REJECTED` 单次恢复、普通 403 不重试以及退出后清理。
- Web 组件测试覆盖初始化/登录分支、表单错误、`/me` 401、Workspace 不匹配和退出行为。
- 最终验证通过：后端 57 个测试、Web 10 个测试文件共 22 个测试、Agent 11 个测试；同时通过 lint、边界检查、类型检查、生产构建、`make ci` 和 `git diff --check`。
- 按本轮协作约定，浏览器正常登录的人工验证留给使用者执行，不把它声明为自动验证证据。

## 遗留边界

- Workspace 成员管理、切换、服务端资源授权和完整 RBAC 属于会话 09–10，本轮受保护页只建立认证闭环。
- 生产域名、HTTPS、反向代理可信头和 Cookie 参数需要在真实部署配置中验证。
- 多标签页退出后的即时 UI 同步、跨标签 Token 缓存协调可在后续体验优化中处理，不改变服务端立即失效语义。
- CSRF Token 轮换策略目前遵循 Spring Security 与 Session 生命周期；若未来增加多域前端或非浏览器客户端，需要先明确不同认证通道，而不是放宽当前浏览器边界。

## 3 个复盘问题

1. 为什么已经校验 Origin，还需要 CSRF Token？  Origin 表示请求声称来自哪个站点，但它受到客户端类型、代理转发和浏览器行为的影响；Token 进一步证明调用方获得了当前 Session 对应的随机秘密。组合校验形成纵深防御，并让缺失 Origin 的非浏览器写请求默认失败。
2. 为什么前端只在 `CSRF_REJECTED` 时重试一次，而不是遇到所有 403 都重试？  403 也可能表示真实的权限不足或 Origin 配置错误。只识别稳定错误码可避免掩盖授权问题；单次上限则防止 Token 获取或服务端配置异常造成无限请求循环。
3. 为什么受保护页面仍要调用 `/me`，不能直接信任登录响应和 Workspace URL？  登录响应会过期，URL 由客户端控制，二者都不是当前权限事实。`/me` 让服务端基于当前 Session 和 MySQL 事实重新确认身份与 Workspace；后续具体资源操作还必须继续在服务端执行成员关系和 RBAC 校验。
