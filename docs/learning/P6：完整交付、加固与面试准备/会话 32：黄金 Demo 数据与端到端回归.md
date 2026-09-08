# 会话 32：黄金 Demo 数据与端到端回归

## 结果与设计

“增加手机号验证码登录”已固化为可重复装载的黄金交付链。空库可在 Flyway V1-V25 之后直接装载，已有样例库重复执行也会收敛到同一事实。最终页面可同时回溯 Delivery Graph 和 Agent Trace。

```text
golden-demo.sql
  -> MySQL 权威事实
  -> forge-server 带 workspace/project scope 的 MyBatis 查询
  -> DeliveryGraphQuery 按资源类型再授权
  -> forge-web Delivery Graph / Agent Run
  -> Playwright 从真实登录会话验证回溯
```

GitLab Demo Adapter 实现既有 `SourceControlProvider` SPI，仅在 `forge.gitlab.provider=demo` 时启用；真实 HTTP Adapter 仍是默认。Agent 继续使用现有 `LanguageModel` SPI 的确定性实现，没有在前端或业务层硬编码成功结果。

## 数据与事务

样例 ID 仅使用 `32000-32999`，全部写入位于一个事务内，并按外键顺序执行。`ON DUPLICATE KEY UPDATE` 会恢复用户可见文案、状态和固定版本；`SET NAMES utf8mb4` 避免宿主机与容器客户端字符集不一致。

Delivery Graph 的 MR/Pipeline 来自 GitLab 标准化缓存，QA、Release 和 Deployment 来自 Server 事实表。外部调用不发生在持有数据库行锁的事务内。

## 安全与风险

- 黄金凭据只用于本地 Demo，不是生产 Secret。
- GitLab 基址使用 `.invalid`，Demo Adapter 不读 Token，不访问真实远程系统。
- 部署事实明确为 `SIMULATED`，仍保留 HIGH 审批和双人控制语义。
- Graph 外部节点分别检查 `repo.read`、`qa.read`、`release.read`，不通过已授权根节点泄露其他领域事实。
- Playwright 默认只在失败时留截图和 Trace，避免无界产物增长。

## 关键文件与代码

- `deploy/demo/golden-demo.sql`：完整、确定、可重放的业务事实。
- `scripts/load-golden-demo.sh`：通过 Compose MySQL 客户端一键装载。
- `DemoSourceControlProvider.java`：按 SPI 提供无网络、确定性 GitLab 行为。
- `DeliveryGraphMapper.java`：每条 SQL 显式保留 workspace/project scope。
- `DeliveryGraphQuery.java`：处理可达性、逐类授权、深度/节点上限和稳定排序。
- `golden-demo.spec.ts`：从真实浏览器登录到最终回溯的黄金链。
- `golden-demo.jsonl`：一条正向和九条稳定负向 Eval 约束。
- `scripts/test-golden-regressions.sh`：把负向场景路由到真实领域测试。

## 验证证据

- `GoldenDemoSeedIntegrationTest`：真实 MySQL + Flyway V1-V25，连续执行种子两次后断言完整链。
- `DeliveryGraphQueryTest`：验证外部节点的父链展开和未授权过滤。
- `DemoSourceControlProviderTest`：验证 Demo GitLab 输出可重现。
- `test_golden_demo_eval_gate.py`：验证 1 正 + 9 负样例的 Tool/权限/结构门禁。
- Playwright Chromium：真实 Compose 应用上黄金回溯 1/1 通过。

## 遗留项

- MySQL 8.4 对 `VALUES(column)` upsert 发出弃用警告；当前仍可用，后续需单独治理，本轮不改历史风格。
- E2E 依赖本机已安装 Playwright Chromium。
- 本轮没有实现下一会话的性能、安全或可观测性加固项。

## 阶段复盘问题

1. 为什么黄金 Demo 必须基于 MySQL 事实和 Adapter SPI，而不是前端硬编码成功数据？
2. 有 Requirement 读权限时，为什么 Graph 仍要对 GitLab、QA 和 Release 节点分别授权？
3. 如何判断 Agent Eval 可以作为稳定门禁，而不会因模型文案波动偶发失败？
