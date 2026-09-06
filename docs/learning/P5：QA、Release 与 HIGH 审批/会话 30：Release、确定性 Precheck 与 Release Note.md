# 会话 30：Release、确定性 Precheck 与 Release Note

## 我完成了什么

- 新增独立的 `releases`、`release_items` 与只追加 `release_prechecks`，版本名在项目与环境内唯一。
- Release Candidate 聚合 Requirement、MR head/latest Pipeline、最新 Test Run、Bug、Release Note 和审批策略，不复用 RELEASE Work Item。
- 新增固定六项 `PrecheckRuleRegistry`；PASS/FAIL 只由 `forge-server` 根据 MySQL 事实计算。
- 每次 Precheck 都追加完整检查结果与关键资源版本；Release Note、Requirement、Pipeline、Test Run、Bug 或策略变化后，旧快照显示为非 current。
- 新增 Release REST API 与 UI，可创建 Candidate、编辑 Note、重复运行 Precheck，并逐项展示失败引用。
- 新增 Release Agent Skill：只读既有 Precheck，以 MEDIUM Tool 更新 Release Note；没有运行 Precheck 或部署的 Tool。

## 我理解的核心设计

Release 是一次“准备发布哪些交付资产、面向哪个环境、使用哪份策略”的独立聚合。Requirement 仍负责自身生命周期，Release 不应伪装成一个 Work Item，否则多 Requirement、多环境、重复候选版本与独立检查历史都会被混入工作项状态机。

Precheck 快照同时保存结论和证据版本。快照便于解释“当时为什么通过或失败”，但它不是永久通行证；读取 Release 时会重新投影当前版本指纹，与最近快照对比。重新 QA 即使最终仍 PASS，也会因 Test Run 版本或标识变化使旧快照失效。

Agent 负责组织语言与起草 Note，不负责确定性裁决。`get_release_precheck` 只返回 Backend 已计算的结果，`update_release_note` 受权限、MEDIUM 确认、幂等和 Release version 约束。部署入口在本轮不存在。

## 调用链

人工操作：

`ReleasePanel → ReleaseController → ReleaseService → PermissionEvaluator → ReleaseStore/PrecheckRuleRegistry → MyBatis → MySQL`

Release Agent：

`Agent Run → Release Skill allowlist → Tool schema/permission/risk/idempotency → ReleaseService → MySQL`

快照有效性：

`GET Release → 当前聚合事实版本投影 → 对比 latest release_prechecks.resource_versions_json → current`

## 数据与事务边界

- MySQL 是 Candidate、Item 集合、Note、规则输入和 Precheck 快照的唯一事实来源。
- 创建 Candidate 与插入全部 `release_items` 位于同一短事务；任一 Item 越租户、越项目或不是 Requirement 时整体失败。
- Note 更新使用 Release 乐观锁并递增 version；Precheck 在单个本地事务中读取规则事实、计算结果并追加快照，不调用 GitLab 或模型。
- MR/Pipeline 是已同步到本地的受控快照；检查要求 latest Pipeline 的 commit SHA 等于 MR 当前 head SHA 且状态成功。
- `release_prechecks` 没有更新入口。重复执行产生新行，历史结论不会被覆盖。

## 失败路径

- Item 未到 `READY_FOR_RELEASE`：返回未就绪 Item key。
- 缺 MR/Pipeline、Pipeline 非成功或 SHA 不等于 MR head：返回 Item、MR 与 head 引用。
- 最新 Test Run 不存在、未完成、FAIL 或 BLOCKED：返回 Run 与失败统计。
- 存在 OPEN/REOPENED 的 BLOCKER/CRITICAL Bug：返回 Bug key。
- Release Note 为空：`ARTIFACTS_PRESENT` 失败。
- 当前项目没有可用 Release Approver，或审批 TTL 非法：`APPROVAL_POLICY` 失败。

## 测试证据

- `PrecheckRuleRegistryTest`：六项规则全通过，以及六类独立失败输入。
- `ReleasePersistenceIntegrationTest`：真实 MySQL 验证版本唯一、重复 Precheck 追加、重新 QA 使旧快照失效。
- `FlywayMigrationIntegrationTest`：24 个迁移、表/字段中文 COMMENT 与 Release 权限。
- `release-panel.test.tsx`：六项 Backend 结论展示与陈旧快照警告。
- `test_release_eval_gate.py`：Release Skill 禁止运行 Precheck 和 deploy，Note 写入要求 MEDIUM 确认。

## 风险与遗留问题

- 当前 Pipeline/QA 查询以本地最近同步事实为准；同步新鲜度告警与故障演练属于后续加固，不在本轮扩大。
- Release Note 当前保存为 Release 内文本，并保留 `release_note_document_id` 扩展引用；若后续要求完整不可变文档版本工作流，应在明确设计后接入，不回改历史迁移。
- 本轮只验证审批人可用与 TTL 合法，不创建 HIGH 审批、不执行或模拟 Deployment；这些属于会话 31。
- 完整黄金 E2E 和跨阶段 `make ci` 留到阶段复盘，按本轮要求未运行。

## 复盘问题

1. 为什么 Release 不直接复用 RELEASE Work Item，它额外承载了哪些聚合不变量？
2. Precheck 同时保存逐项结论和资源版本后，如何兼顾历史可解释性与执行前新鲜度？
3. 为什么 Agent 可以解释失败和起草 Note，却不能运行或决定 Precheck PASS？
