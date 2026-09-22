# Tasks

- [x] Task 1: forge-server 新增「删除需求」接口（仅草稿，软删除）
  - [x] SubTask 1.1: `OrganizationRequirementStore` 增加 `softDelete(organizationId, requirementId, expectedVersion)` 契约
  - [x] SubTask 1.2: `OrganizationRequirementMapper` 增加软删除 SQL：`UPDATE work_items SET deleted_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6), version = version + 1 WHERE id = #{requirementId} AND organization_id = #{organizationId} AND type = 'REQUIREMENT' AND deleted_at IS NULL AND version = #{expectedVersion}`，并在 `MybatisOrganizationRequirementStore` 实现（更新 0 行时由服务层判定失败原因）
  - [x] SubTask 1.3: `OrganizationRequirementService.delete` 实现：解析 Session 对应的公司 scope（单公司部署下恒为唯一公司）→ `permissionEvaluator.requireOrganization(..., "requirement.edit")` → 行锁与读取需求 → 非 `REQUIREMENT` 或不存在/已删除抛 `ResourceNotFoundException` → 状态非 `DRAFT` 抛 `InvalidTransitionException`（409）→ 软删除影响 0 行抛 `VersionConflictException`（409）
  - [x] SubTask 1.4: `RequirementController` 增加 `DELETE /{requirementId}`，查询参数 `expectedVersion`（`@PositiveOrZero`），返回 204
  - [x] SubTask 1.5: 新增集成测试 `RequirementDeletionIntegrationTest`：草稿删除成功且随后详情/列表返回 404；非草稿返回 409 且数据未变；版本过期返回 409；重复删除或不存在返回 404

- [x] Task 2: forge-web 补齐需求操作 API 客户端
  - [x] SubTask 2.1: 在 `work-item-api.ts` 扩展基础字段更新能力，支持可选 `priority` 与 `dueAt`（只发送本次变更字段 + `expectedVersion`），保持既有「编辑描述」调用方兼容
  - [x] SubTask 2.2: 在 `work-item-api.ts` 新增 `deleteRequirement(id, expectedVersion)`，调用 `DELETE /v1/requirements/{id}?expectedVersion=`
  - [x] SubTask 2.3: 补充 `work-item-update-api.test.ts`（或等价测试）覆盖新增/变更的请求方法、路径与请求体

- [x] Task 3: 需求列表行内简单操作
  - [x] SubTask 3.1: 重构 `requirement-list.tsx` 行结构：信息区保留行点击跳转 `/requirements/{id}`，操作区独立渲染，避免交互控件嵌套在 `Link` 内；同步调整 `requirement-list.module.css`
  - [x] SubTask 3.2: 实现「修改优先级 / 期望发布时间」弹窗：优先级下拉使用既有 `priorityLabel` 取值集合，期望发布时间使用日期选择（不支持清空）；提交后 `invalidateQueries` 刷新列表，失败用 `Message.error` + `formatRequestError`
  - [x] SubTask 3.3: 实现「删除需求」：仅当 `item.status === "DRAFT"` 时展示入口，弹出确认后调用 `deleteRequirement(id, item.version)`，成功后提示并刷新列表
  - [x] SubTask 3.4: 新增 `requirement-list.test.tsx`：断言行链接指向 `/requirements/{id}`；断言修改提交携带 `expectedVersion` 与变更字段；断言删除确认后调用删除接口；断言非草稿行不出现删除入口

- [x] Task 4: 新增需求列表页与二级页导航层级
  - [x] SubTask 4.1: 新增 `requirement-list-page.tsx`（标题「需求列表」+ 「新建需求」入口 + `<RequirementList />` 全量需求列表）及其样式模块，并从 `features/work-item/index.ts` 导出
  - [x] SubTask 4.2: 新增路由 `src/app/requirements/page.tsx`，使用 `ProtectedApp` 包裹需求列表页
  - [x] SubTask 4.3: `app-shell.tsx` 主导航新增「需求列表」项（`/requirements`），当 `pathname` 为 `/requirements` 或以 `/requirements/` 开头时高亮；`pageName` 补充 `/requirements` → 「需求列表」
  - [x] SubTask 4.4: `organization-requirement-detail.tsx` 返回入口改为「← 返回需求列表」并指向 `/requirements`
  - [x] SubTask 4.5: 更新 `app-shell.test.tsx` 导航断言，并检查 `my-requirements.test.tsx` 等既有用例是否受影响并修复

- [x] Task 5: 端到端验证与质量门禁
  - [x] SubTask 5.1: forge-server 运行新增集成测试与既有 Requirement 相关集成测试
  - [x] SubTask 5.2: forge-web 运行受影响 Vitest 用例（列表、导航、详情）
  - [x] SubTask 5.3: 根目录运行 `make ci` 与 `git diff --check`
    - `git diff --check` 通过。
    - `make ci` 在 `build` 之前的所有门禁（format-check、lint、contracts-check、test）全部通过；仅 `next build` 未通过，报 `Error occurred prerendering page "/_not-found"` / `ReferenceError: Cannot access 'am' before initialization`。
    - 该 build 失败为既有问题、非本变更引入：将 `app-shell.tsx` 用 `git stash` 回退到 HEAD 版本、并 `rm -rf .next` 清缓存后仍复现同样错误（验证后已 `git stash pop` 恢复本变更）。

# Task Dependencies

- Task 2 depends on Task 1（删除接口契约确定后才能接入客户端）
- Task 3 depends on Task 2
- Task 4 depends on Task 3（列表页复用带操作列的列表组件）
- Task 5 depends on Task 1、Task 2、Task 3、Task 4
- Task 1 与 Task 4 的 SubTask 4.3/4.4 无直接耦合，可在 Task 1 完成后并行推进
