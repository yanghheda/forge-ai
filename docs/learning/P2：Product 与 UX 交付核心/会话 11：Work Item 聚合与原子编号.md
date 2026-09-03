# 会话 11：Work Item 聚合与原子编号

## 我完成了什么

- 用户可以通过统一 Work Item API 创建、查询、分页和编辑 Requirement、UX Task、Dev Task、QA Task。
- `itemKey`、`itemNumber`、创建状态和 reporter 全部由服务端决定，客户端不能伪造。
- Requirement 固定以 `DRAFT` 创建，三类 Task 固定以 `TODO` 创建。
- 项目创建时同步初始化独立编号序列；升级时为已有项目回填序列。
- PATCH 使用 `expectedVersion`，冲突返回 `409 VERSION_CONFLICT`，不会静默覆盖。
- 列表按项目范围分页、按编号倒序，并支持 type/status 枚举筛选。

入口到结果的调用链：

```text
Cookie Session + CSRF
  → WorkItemController
  → WorkItemCommandService / WorkItemQueryService
  → PermissionEvaluator（Workspace、Project Member、角色权限）
  → WorkItemStore
  → WorkItemMapper（显式 workspace_id + project_id SQL）
  → MySQL
```

## 我理解的核心设计

### 关键不变量

- 每个 Project 只有一条 `project_item_sequences`，不同项目互不争用同一行。
- Work Item 编号只增不减。逻辑删除不会删除序列事实，也不会释放唯一键。
- `item_key = project.key + "-" + item_number`，两部分均在服务端事务中取得。
- type 决定初始 status 和 `requirement/ux/task` 权限资源；普通 PATCH 不能修改 type/status/编号。
- 资源 ID 不是授权依据；详情、分页和更新 SQL 都显式包含 Workspace 与 Project scope。

### 事务与一致性边界

创建 Work Item 的本地事务锁定当前项目的 sequence 行，读取 `next_value`，递增 sequence，再插入 Work Item。任一步失败时整个事务回滚。锁只覆盖同一项目的短事务，不包含外部调用。

编辑采用乐观锁：最终 `UPDATE` 自身携带 `version = expectedVersion` 条件，并原子执行 `version = version + 1`。Java 层预读用于授权和合并基础字段，不能代替 SQL 的版本条件。

编号允许出现缺口，因为编号的职责是唯一、稳定、永不复用，而不是表达业务数量。为了消除缺口而回退序列，会在并发或重试时引入重复身份风险。

### 权限与安全边界

- Requirement 使用 `requirement.read/create/edit`。
- UX Task 使用 `ux.read/create/edit`。
- Dev/QA Task 使用 `task.read/create/edit`。
- 无权、跨租户、错误 Project scope、已逻辑删除统一表现为 404，减少 ID 枚举信息。
- assignee 若存在，必须是当前 Workspace 和 Project 的有效成员。

### 为什么领域模型不等于数据库行

`work_items` 为后续会话预留了 parent、severity、blocked、deleted 等持久化字段；本轮 `WorkItem` 领域对象只暴露已经有明确行为的基础能力。Mapper 负责行映射，Application Service 负责用例、授权和不变量，领域类型不依赖 MyBatis 或 Controller DTO。

## 失败路径

- 并发创建：同一项目的请求在 sequence 行锁处排队，最终各自得到唯一编号。
- 并发编辑：两个请求读取同一版本时，只有先执行的 UPDATE 命中；后一个返回版本冲突。
- 逻辑删除：普通详情和分页不可见，但 sequence 不回退，下一项继续使用更大编号。
- 类型权限不足：应用服务在调用 Store 前返回资源不可见，不依赖前端隐藏按钮。
- 非法分页：在查询 MySQL 前返回 `400 VALIDATION_FAILED`。
- 编号缺口：允许保留，不执行修复或复用；业务数据仍保持唯一和可追溯。

## 测试证据

- `WorkItemTypeTest.requirementAndTasksChooseTheirFixedCreationStatus`：固定四种类型的创建初值。
- `WorkItemIntegrationTest.createsRequirementAndTasksWithServerGeneratedKeysAndFixedInitialStatuses`：真实 HTTP 到 MySQL 的创建链路。
- `WorkItemIntegrationTest.fiftyConcurrentCreatesAllocateUniqueMonotonicProjectNumbers`：50 个并发事务得到唯一的 `1..50`，序列最终为 51。
- `WorkItemIntegrationTest.twoConcurrentPatchesWithOneExpectedVersionHaveOneWinner`：两个并发 PATCH 只有一个成功。
- `WorkItemIntegrationTest.logicalDeletionDoesNotReleaseItsNumber`：删除 `FORGE-1` 后新建得到 `FORGE-2`。
- `WorkItemIntegrationTest.readsPagesThroughProjectScopeAndUsesTheProjectTypeStatusIndex`：分页内容正确，MySQL `EXPLAIN` 使用项目/type/status 索引。
- `FlywayMigrationIntegrationTest.workItemMigrationCreatesScopedSequenceAndAggregateTables`：V7 表、索引和中文元数据注释存在。

这些测试不能证明后续 Transition/Guard、关系、Activity、Outbox 或通用幂等；这些能力不属于会话 11。

## 遗留问题

- PATCH 中显式清空 assignee/dueAt 需要稳定的字段清除语义；本轮 `null` 表示保留当前值，后续 UI 契约确定时补充。
- Work Item Activity、审计事件和 Outbox 尚未落地，不在本轮提前创建空架构。
- Requirement Details、父子关系、Bug/Release 类型及完整状态集合由后续会话逐步加入。

## 3 个复盘问题

1. 为什么编号分配使用悲观行锁，而普通编辑使用乐观锁？
   - 我的回答：编号分配必须保证“同一项目内唯一且按分配顺序递增”，并发创建时不能出现重复号，所以用 SELECT ... FOR UPDATE 锁住该项目唯一的序列行。基础字段编辑则不要求串行，使用 version 比较即可在冲突时拒绝旧写入，吞吐更高。
2. 为什么编号缺口可接受，而编号复用不可接受？
   - 我的回答：号是业务历史标识，不是可回收资源。删除 FORGE-7 后重用它，会让链接、审计记录、通知、外部引用和缓存把新旧两个不同 Work Item 混为一谈。历史缺口只影响连续性，不破坏身份唯一性，因此可以接受。
3. 如果删掉 PATCH SQL 中的 `version = expectedVersion` 条件，两个并发请求会如何破坏数据？
   - 我的回答：去掉 version = expectedVersion 后，两个客户端都可基于同一旧版本成功写入：后提交者会静默覆盖先提交者的字段，客户端仍以为自己的编辑被完整保留。这就是“丢失更新”；当前实现会让第二个写入返回 409，要求客户端刷新后决定如何合并。
