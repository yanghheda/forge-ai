# 会话 05：Agent 骨架、契约目录与三应用 Smoke

## 我完成了什么

- 用户可见结果：`forge-agent` 可以启动，三应用与 MySQL、Redis、Qdrant 可以通过 Compose 一起运行，`make smoke` 验证 Web、Server、Agent 的最小链路。
- 建立 Pydantic Settings、公开 liveness、受保护的内部 readiness、空 `RuntimeGateway`、Tool/Skill 契约 Schema 和校验命令。
- Web→Server 链路：访问 Web `/api/v1/system/status` → Next rewrite → Server 状态接口 → 结果返回 Web。
- Server→Agent 链路：访问 Server `/actuator/health/agent` → `AgentHealthIndicator.health()` → `AgentServiceTokenProvider.createToken()` → 请求 Agent `/internal/v1/health/ready` → `require_internal_credential()` 校验 JWT → `readiness()` 返回 `READY`。

## 我理解的核心设计

- 关键不变量：`forge-server` 是业务事实、权限和事务的唯一权威；Agent 不能直接写业务数据库，也不能持有 GitLab Token 或部署凭据。
- 关键不变量：Agent 内部端点不能因为位于内部网络就跳过认证。它只接受 issuer/subject 为 `forge-server`、audience 为 `forge-agent`、未过期且签名正确的 Token。
- 关键不变量：Contract 是执行前的安全边界。Tool 必须声明权限、风险、幂等、超时、重试、敏感字段和后端映射；Skill 只能使用 allowlist 中真实存在的 Tool。
- 服务 Token 每次探测重新签发并限制为最多五分钟，缩短凭据泄露后的重放窗口；两端都验证共享密钥的最小长度。
- liveness `/healthz` 只证明 HTTP 进程存活；readiness `/internal/v1/health/ready` 证明内部配置和认证链路可用，两者语义不能混淆。
- Agent 故障被 Server 表达为 `DEGRADED`，且不进入核心 readiness group，不能因为可选 Agent 能力不可用就声称 MySQL 业务事实不可用。
- 事务/一致性边界：当前 Agent 没有业务 Tool；未来写操作必须通过受控 Server Tool API，由 Server 重新执行权限和状态校验。
- 权限与安全边界：Compose 只给 Agent 注入内部 JWT Secret，不提供 MySQL、GitLab 或部署凭据；Agent 位于内部网络且不映射宿主机端口。
- 为什么没有虚构业务 Tool：对应的 Server 应用服务和权限模型尚未实现，提前声明 Endpoint 会制造无法兑现、无法验证的安全契约。

## 失败路径

- 触发方式：内部 readiness 缺少 Token、签名错误、audience/subject 错误、Token 过期；Tool 缺少风险字段；Skill 引用不存在的 Tool；Agent 网络不可达或返回非 2xx。
- 系统如何失败：Agent 对无效凭据统一返回 HTTP 401，不泄露具体校验细节；契约校验器以非零状态阻断 CI；Server 将 Agent 健康报告为 `DEGRADED`。
- 数据是否保持正确：当前 Agent 不连接业务数据库，无效请求和 Agent 故障不会修改 MySQL 事实；降级只影响可选 Agent 能力。
- 如何定位与恢复：从 Server Agent health component 的 `reason` 区分 HTTP 状态、超时和连接异常；核对两端 Secret、issuer、audience 与时间；契约错误根据文件和字段路径修复。

## 测试证据

- `forge-agent/tests/test_health.py`：验证公开 liveness，以及内部 readiness 对有效、缺失、错误 audience 和过期 Token 的行为。
- `AgentServiceTokenProviderTest`：验证 Server 签发的 claims 和有效期，以及过短 Secret、非法 TTL 被拒绝。
- `AgentHealthIndicatorTest.sendsBearerCredentialAndRequestId`：验证 Server 调用 Agent 时发送 Bearer Token 和 requestId。
- `authenticationFailureIsDegradedInsteadOfCoreReadinessFailure`：验证 Agent 返回 401 时 Server 报告 `DEGRADED`。
- `test_contract_validation.py`：验证仓库契约有效、缺少 `risk_level` 失败、Skill 引用未知 Tool 失败。
- `tests/three-app-smoke.sh`：构建完整 Compose，验证 Web→Server、Server→Agent，以及 Agent 拒绝无效凭据。
- 它不能证明什么：Smoke 不能证明未来 Tool 的业务权限、幂等、审批和重试逻辑；JWT 测试也不能消除有效期内 Token 被窃取后的重放风险。

## 仍不清楚的问题

- 从共享 HS256 Secret 迁移到非对称签名或工作负载身份的触发条件是什么？
- Agent 调用 Tool 时，用户身份、Workspace 范围和审批上下文应如何绑定到不可重放的一次调用？
- Tool Contract 升级如何兼容正在运行或等待恢复的 Agent Run？

