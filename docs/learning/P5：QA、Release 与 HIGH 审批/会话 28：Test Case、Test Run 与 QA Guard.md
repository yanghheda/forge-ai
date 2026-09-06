# 会话 28：Test Case、Test Run 与 QA Guard

## 我完成了什么

- 新增 `test_cases`、`test_runs`、`test_results` 三张带 Workspace/Project 范围、中文元数据注释和乐观锁版本的表。
- Test Case 支持前置条件、有序步骤、预期结果和 P0/P1/P2 优先级。
- 创建 Test Run 时，将当时全部 ACTIVE Test Case 固化为该 Run 的 `NOT_RUN` Test Result 集合。
- Test Result 支持 PASS、FAIL、BLOCKED、SKIPPED 和证据引用，并以 `expectedVersion` 防止并发覆盖。
- 完成 Run 时由 Server 重新统计并固化 `summary_json`；完成后拒绝结果修改，必须显式 reopen。
- reopen 清空旧统计、恢复 IN_PROGRESS，并在同一事务追加 `QA_RUN_REOPEN` 审计。
- 注册 `START_QA`（READY_FOR_QA → IN_QA）和 `QA_PASS`（IN_QA → READY_FOR_RELEASE）；QA PASS 使用纯确定性 Guard。
- Requirement 页面新增 QA 面板，用于创建用例、创建/恢复最新 Run、填写结论、完成和 reopen。

## 核心概念

Test Case 是可复用的测试设计；Test Result 是某个 Case 在某次 Run 中的执行事实。Run 是执行边界：它固定本次覆盖集合、执行环境、执行人和最终统计，因此后续新增 Case 不会悄悄改变已完成 Run 的结论。

QA Guard 只消费最新已完成 Run 的固化统计：P0/P1 必须 PASS，P2 可以 SKIPPED，任何 NOT_RUN、FAIL、BLOCKED 都阻断。Agent 可以辅助生成或解释材料，但没有 QA Tool，不能生成最终放行事实；状态转换仍要求当前用户拥有 `qa.execute`。

## 调用链

页面执行链：

`RequirementDetail → QaPanel → QaController → QaService → PermissionEvaluator → QaStore → QaMapper → MySQL`

放行链：

`RequirementDetail → WorkItemController → RequirementTransitionService → RequirementWorkflowRegistry → QaPassGuard → QaStore → test_runs.summary_json`

## 数据与事务边界

- MySQL 是 Test Case、Run、Result、固化统计与 reopen 审计的唯一事实来源。
- 创建 Run 和快照 Result 在一个短事务中完成；没有可执行用例则整体回滚。
- Result 更新通过 Run 状态与 Result version 单条条件更新，已完成 Run 不可写，并发相同版本只有一个成功。
- 完成 Run 在同一事务内读取当前 Result 统计并以 Run version 条件固化；不调用外部系统。
- reopen 的状态更新与审计追加属于同一事务，失败不会留下半条事实。
- Requirement 状态仍由既有 Transition Store 原子写入；QA 模块不直接改 Work Item 状态。

## 常见误区

把 Test Case 当前状态直接聚合成发布结论会让历史随设计变动而漂移。正确边界是 Run 拥有当次 Result 集合，完成统计成为可审计快照；需要修订时显式 reopen，而不是静默改历史。

## 风险与遗留问题

- 本轮按设计默认允许 P2 SKIPPED，尚未引入可配置的 P2 项目策略。
- evidence 当前保存脱敏字符串引用，未实现附件上传或证据对象；附件能力应复用后续明确设计，不能在本轮扩展。
- reopen 已让 QA Guard 只选择仍为 COMPLETED 的最新 Run；Release Precheck 失效属于会话 30，当前没有提前实现。
- Bug 创建、Bug 回流、QA Agent 和 AI 生成 Test Case 属于会话 29，本轮没有实现。
- UI 提供本轮最小闭环，实际结果与证据的富输入体验可在不改变服务端规则的后续 UI 改进中补充。

## 复盘问题

1. 为什么 Test Result 必须属于特定 Test Run，而不能只在 Test Case 上保存“最后一次结果”？
2. 为什么已完成 Run 的结果需要显式 reopen，且 reopen 必须与审计记录处于同一事务？
3. 为什么 QA PASS 必须由 Server 的确定性统计计算，而不能接受 Agent 输出的“建议通过”？
