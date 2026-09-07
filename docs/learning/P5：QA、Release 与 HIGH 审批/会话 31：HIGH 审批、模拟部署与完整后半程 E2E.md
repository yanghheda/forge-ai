# 会话 31：HIGH 审批、模拟部署与完整后半程 E2E

## 本轮结果

Release 的人工入口与 Agent `deploy_release` Tool 都只能执行 `SIMULATED` 部署。人工入口创建
`PENDING_APPROVAL` 记录，Agent Tool 无论 Run 的 MEDIUM 策略为何都进入 HIGH 审批。批准前不产生部署副作用，
自审批、过期审批、资源版本变化与重复提交均由 Server 权威事实拒绝或收敛。

## 设计与调用链

人工调用链：Release 页面申请 → `ReleaseController` → `DeploymentService` 校验 `release.deploy`、当前 PASS
Precheck 与幂等键 → 冻结 Release version、Precheck id、参数 hash 和 TTL → 另一用户使用
`approval.decide` 决策 → worker 乐观锁抢占 → 确定性模拟器 → 更新 Deployment/Release → Requirement 依次执行
`MARK_RELEASED`、`CLOSE_REQUIREMENT` 并追加事件。

Agent 调用链：Release Skill 选择 `deploy_release` → Registry 校验 Skill、Schema、权限与 HIGH 风险 →
`ApprovalService` 冻结 Tool 版本、参数和 Release version → Run 暂停 → 另一用户批准 → 恢复时重新校验权限、
TTL、Tool/参数和 Release version → 以原 Tool Call 幂等键创建 `APPROVED` 模拟部署 → 同一 worker 执行。
HIGH 失败只形成结构化失败结果，不由 Agent 自动重试。

## 数据与事务边界

- MySQL 是审批、部署、Release、Requirement、Audit 与 Trace 的唯一事实来源。
- 请求事务只冻结审批与部署输入，不执行环境调用；MVP 模拟器没有外部系统副作用。
- 审批事务使用 `status + version + expires_at` 条件更新，阻止重复决定和过期批准。
- worker 使用乐观锁从 `APPROVED` 抢占为 `DEPLOYING`；单个本地事务内写模拟结果、Release 状态、
  Requirement 两段状态事件与审计记录。
- Release 的编排状态变化不递增内容版本，避免申请审批本身使刚生成的 Precheck 自我失效；Release Note 等
  内容变化仍递增版本并使旧 Precheck/审批失效。

## 关键风险

- `SIMULATED` 不是生产部署成功。API、数据库结果摘要与 UI 均保留显著标识。
- 当前 worker 是单库轮询模型；多实例依靠乐观锁避免重复抢占，但没有租约和崩溃后 `DEPLOYING` 回收。
- 模拟失败开关只用于确定性验证，不应原样扩展到真实 Deployment Provider。
- 人工部署审批与 Agent Tool 审批是两个入口，但最终共享 Deployment 状态机；真实部署接入前需新增 ADR，
  定义凭据、Provider、超时、补偿与外部幂等边界。

## 逐文件学习索引

- `V25__simulated_deployments.sql`：Deployment 冻结事实、索引、中文 COMMENT 与 `release.deploy` 权限。
- `DeploymentService.java`：PASS Precheck、双人控制、TTL、资源版本与请求幂等的应用规则。
- `DeploymentMapper.java` / `MybatisDeploymentStore.java`：显式租户 scope、条件更新、状态事件和审计 SQL。
- `SimulatedDeploymentWorker.java` / `SimulatedDeploymentExecutor.java`：抢占、确定性执行与联动收敛。
- `ApprovalService.java` / `AgentToolExecuteService.java`：HIGH 无条件审批、冻结 Release version 和恢复执行。
- `deploy_release.yaml` / `release.yaml`：Release Skill 白名单、HIGH 风险和单次尝试契约。
- `release-api.ts` / `release-panel.tsx`：人工申请、决定、部署记录与非生产提示。
- `ReleasePersistenceIntegrationTest.java`：迁移、幂等、乐观锁和后半程状态联动证据。
- Agent Eval 与 Web 组件测试：Tool 安全边界和 UI 解释性证据。

## 验证证据

- Server 单元：`ToolContractRegistryTest,SimulatedDeploymentExecutorTest` 通过。
- Server 集成：`ReleasePersistenceIntegrationTest` 通过，Flyway 从空库应用至 V25。
- Agent：Release Eval 与 Contract Registry 共 10 项通过。
- Web：Release Panel 共 2 项通过。
- 契约：仓库 Tool/Skill schema 校验通过。

## 遗留问题

1. `DEPLOYING` worker 崩溃后的租约回收与告警属于后续可靠性加固，不在会话 31 扩展。
2. 真正的环境 Provider、Secret、回滚和生产凭据明确未实现；接入时必须先新增 ADR。
3. 会话 32 再补黄金 Demo seed 与跨应用 Playwright 全链回归，本轮不提前实现。

## 复盘问题

1. 为什么 HIGH Tool 必须忽略 Run 的 MEDIUM `ALLOW` 策略，并在恢复时再次检查权限与资源版本？
2. 为什么 Deployment 的编排状态不能与 Release 内容版本使用同一个递增语义？
3. 从模拟器升级到真实部署 Provider 时，哪几个事务外部调用与崩溃空窗必须用幂等和 reconcile 处理？
