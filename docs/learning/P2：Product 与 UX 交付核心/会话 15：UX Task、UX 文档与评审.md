# 会话 15：UX Task、UX 文档与评审

## 本轮完成

- Product Review 批准后，在同一事务内创建并关联一个 UX Task；项目编号仍通过序列表行锁分配。
- UX Task 只能按 `TODO → IN_PROGRESS → IN_REVIEW → DONE` 完成；其 DONE 不改变 Requirement 的 UX 阶段状态。
- Requirement 的 UX Review 必须具备已发布 `UX_SPEC`，并冻结用户流、页面清单、关键交互和异常态检查项。
- 每个提交、退回和批准都追加 `review_records`、`work_item_events`；批准时保存已发布交付物的确切文档版本快照。
- UX 角色获得 UX 文档创建、编辑、发布和 UX Review 权限；Web 提供 UX 队列入口及 Requirement 内 UX Spec/评审清单。

## 调用链与边界

Web 只提交 `action`、`expectedVersion`、幂等键与 UX checklist。`forge-server` 重新按项目范围加载 Work Item、校验权限和 Guard，再在同一个数据库事务中写状态、Review、事件和自动 UX Task。文档版本早已是不可变追加写；Review 保存版本 ID，而不是“当前文档”的可变引用。

MySQL 是状态、版本、评审和编号的唯一事实来源。没有 Agent、外部系统调用或跳过 UX 策略进入本轮实现；跳过 UX、关系和统一 Activity 留给会话 16。

## 验证

- `./mvnw -q -Dtest=RequirementTransitionIntegrationTest test`：通过，覆盖 UX 交付主流程、缺 UX Spec、UX Task 不越关、快照及既有并发版本测试。
- `./mvnw -q -Dtest=RequirementWorkflowRegistryTest,DocumentContentTest,ProductionJavaMemberCommentTest test`：通过。
- `npm run typecheck && npm run lint && npm test`：通过（18 个 Vitest 测试）。

## 风险与遗留

- UX 队列当前展示任务摘要；UX Task 详情页的独立操作面将在后续页面切片中拆出，不能通过 Requirement 详情接口替代。
- 文档快照已保存所有已发布的 Product/UX 交付物；后续版本发布不会回写历史 Review，但“旧批准是否应失效”的重新提交策略应在文档变更规则中进一步明确。

## 复盘问题

1. 为什么 UX Task 的 DONE 不能直接让 Requirement 进入 READY_FOR_DEV？
2. 为什么评审记录和文档版本必须追加写，而不能覆盖旧记录？
3. 当 UX Spec 发布新版本后，怎样让 UI 清楚提示旧批准对应的版本？

UX Task 只是一个执行单元，DONE 只能说明“这项设计任务完成”。Requirement 进入 READY_FOR_DEV 是阶段准入，仍需验证已发布 UX Spec、完整 checklist、评审权限与批准记录。否则一个完成但交付物不完整的 Task 会绕过业务 Gate。

覆盖旧记录会破坏审计链：无法回答“当时谁基于哪个版本批准、退回理由是什么”。追加写让历史评审、文档版本和状态事件可追溯，也支持并发冲突定位与之后的合规复盘。

UI 应明确展示“当前批准基于 UX Spec vN”，并对比当前版本：

- 当前版本仍是 vN：显示“批准有效”。
- 当前版本已是 vN+1：显示醒目的“交付物已更新，当前批准基于旧版本”。
- 提供“查看版本差异”“打开批准快照”“重新提交 UX Review”三个动作。
