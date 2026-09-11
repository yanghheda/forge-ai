# ForgeAI 分阶段开发指南 v1.0

> **历史开发路线**：会话 06–34 记录早期实现过程，其中 Workspace/Project 相关步骤已经失效。当前开发以公司级架构基线 v2、ADR-016 和现行代码测试为准。

> 为“使用 Codex 辅助、但开发者持续掌握实现细节”的面试项目设计

| 属性 | 内容 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-08-25 |
| 设计基线 | `docs/design/ForgeAI_详细设计_v1.0.md` |
| 推荐节奏 | 每轮 2–6 小时，只完成一个可解释、可测试、可提交的增量 |
| 总体规模 | 7 个阶段、34 个主开发会话；按个人时间可拆成更多轮，禁止合并成“一次完成” |

---

## 1. 这套开发方式解决什么问题

本项目的目标不只是“做出 Demo”，还要让你在面试中能解释：为什么这样分层、数据与事务边界如何确定、权限在哪里生效、Agent 为什么不能越权、故障后如何恢复、测试如何证明系统正确。

因此每轮会话遵循三个约束：

1. **一个核心概念。** 一轮最多引入一个主要技术主题，例如乐观锁、Outbox、SSE 重放或审批恢复。
2. **一个可运行增量。** 不接受只批量建空目录，也不接受跨越多个阶段的大爆炸实现。
3. **一次开发者复述。** 进入下一轮前，你应能脱离 Codex 用自己的话解释本轮的关键路径和至少一个失败分支。

34 轮是推荐上限粒度，不是要求一天一轮。某轮超过约 500–800 行有效变更、涉及超过两个应用或产生无法在 10 分钟内评审完的 Diff，应继续拆分。

---

## 2. 每轮会话的标准协议

### 2.1 开始前：你提供的上下文

每轮新会话只提供：当前会话编号、目标、允许修改的模块、明确不做的内容、上一轮验证结果和工作树状态。相关设计章节由 `docs/context/INDEX.md` 路由，无需在 Prompt 中重复粘贴。不要只说“继续开发”，也不要让 Codex 自行选择下一阶段。

推荐开场 Prompt：

```text
现在执行《ForgeAI 分阶段开发指南 v1.0》的会话 <编号/名称>。
先读取仓库 AGENTS.md 和 docs/context/INDEX.md，再按会话路由只读取命中的设计章节；不要全文读取四份基线文档。

本轮只完成：<目标>。
允许修改：<目录/模块>。
明确不做：<下一轮内容>。

请按以下顺序协作：
1. 先检查现状并用简洁语言说明设计、调用链、数据/事务边界和风险；此时不要改代码。
2. 给出本轮文件级计划、测试计划和预计会出现的关键 Diff，等我理解后再实施。
3. 分小步实现；每完成一个逻辑单元就运行对应测试，不吞掉失败。
4. 完成后逐文件讲解关键代码，给出验证证据、遗留问题和 3 个复盘问题。
不要顺手实现下一轮，不要用 mock 掩盖应由本轮实现的核心规则。
```

第 2 步的“等我理解后”意味着本轮建议分成至少两个用户回合，而不是让 Codex 一次输出计划后立即改完整模块。如果你已经熟悉主题，可明确授权继续。

### 2.2 实施中：Codex 的工作边界

Codex 应当：

- 先读现有代码、测试和文档，保护用户已有改动。
- 先写/更新能表达业务规则的测试，再实现最小代码使其通过。
- 在涉及数据、权限、并发或外部副作用时明确失败路径。
- 使用真实依赖的测试容器或协议 Stub，避免把核心风险全部 mock 掉。
- 编写 Flyway 建表或字段变更时，为每张表和每个字段提供写入 MySQL 元数据的中文 `COMMENT`，并用迁移集成测试抽查关键注释；不得用 SQL 行注释代替。
- 只在本轮范围内重构；发现跨阶段问题时记录，不顺手扩大范围。
- 前端变更按 Feature 拆分页面、组件、Hooks、Schema、API 与工具函数；不把业务逻辑集中到单个 `page.tsx` 或组件文件。

Codex 不应当：

- 一次生成全部三端、数据库、Agent 和部署代码。
- 以“以后补测试”结束涉及权限、工作流、幂等或审批的会话。
- 只展示最终文件，不解释关键选择和替代方案。
- 为通过测试删除断言、放宽授权或隐藏错误。
- 在未经 ADR 的情况下改变详细设计中的安全边界。

### 2.3 结束前：五项出口检查

每轮必须给出：

1. `git diff --stat` 与关键文件清单。
2. 实际运行的格式化、测试、构建命令和结果。
3. 从入口到持久化/外部系统的调用链。
4. 一个已验证的正常路径和至少一个失败路径。
5. 本轮学习卡：核心概念、一个常见误区、三个复盘问题。

你应亲自完成两件事：随机选一个测试说明它证明了什么；随机选一个核心方法说明删掉某段校验会发生什么。答不出来就先开“讲解/复盘会话”，不要急着进入下一轮。

### 2.4 提交策略

建议每轮 1–3 个语义清楚的提交：

```text
chore(repo): bootstrap monorepo quality gates
feat(workitem): allocate project-scoped item keys atomically
test(workflow): cover ux review guard failures
docs(adr): record session authentication decision
```

不要让 Codex 自动 push、合并或改远程设置。提交前人工查看 Diff，尤其是 Migration、权限、配置、依赖锁文件和生成代码。

---

## 3. 总体路线与里程碑

| 阶段 | 会话 | 可演示里程碑 | 主要学习主题 |
|---|---:|---|---|
| P0 工程基础 | 01–05 | 三应用可运行，契约与 CI 骨架存在 | Monorepo、构建、迁移、契约、Compose |
| P1 身份与租户 | 06–10 | Admin 初始化、登录、Workspace/Project 隔离 | Session、CSRF、RBAC、多租户测试 |
| P2 Product/UX 核心 | 11–17 | Requirement → PRD → UX Review → Ready for Dev | 聚合、乐观锁、状态机、版本、Graph |
| P3 Agent 基础与 Product/UX Skill | 18–22 | Agent 受控创建 UX 资产，Trace/SSE 可恢复 | Deep Agents、Tool、RAG、SSE、审批前置 |
| P4 Development/GitLab | 23–27 | Branch/MR/Pipeline 与需求关联 | Adapter、Secret、Webhook、最终一致性 |
| P5 QA/Release | 28–31 | QA 回流、Precheck、审批和模拟部署 | 测试聚合、确定性规则、HIGH 操作 |
| P6 完整交付 | 32–34 | 黄金 Demo、部署文档和面试材料 | E2E、安全、可观测、运维、叙事 |

依赖原则：会话按编号执行。若某轮只完成一半，不把剩余工作暗中带入下一轮；为它增加 `12A/12B` 子会话。

---

## 4. P0：工程基础（会话 01–05）

### 会话 01：仓库基线、ADR 与质量门禁

**结果：** 建立最终目录、统一开发命令、格式/静态检查入口、贡献约定和首批 ADR；所有模块仍可很薄。

**先掌握：** Monorepo 的价值不是“文件放一起”，而是同一 commit 下契约与三端版本一致；ADR 记录为什么而非重复代码。

**Codex 范围：** 创建根 README/Makefile 或 task runner、`.editorconfig`、Git ignore、模块目录、`docs/adr`、CI 骨架；记录 ADR-001/002/010/011/013，并写明“解释性注释优先中文；Java 字段、record 组件、enum 常量与 enum 成员字段必须分别有中文普通块注释（`/* ... */`）”的仓库规则。不要实现业务页面或领域表。

**你要检查：** 命名是否全部使用 `forge-web/server/agent`；根命令是否只是薄封装而非隐藏复杂脚本；CI 是否每个失败都能阻断。

**验证：** 空仓库新克隆后能执行 `make help`（或等价命令）；格式检查能故意制造失败；ADR 有状态、上下文、决策、后果。

**复盘问题：** 为什么三个独立仓库会增加契约漂移？什么变化必须 ADR？质量门禁为何要从第一天存在？

### 会话 02：Backend 骨架与模块边界

**结果：** Java 21/Spring Boot 应用启动，按模块组织，健康检查、Swagger/OpenAPI、统一错误信封和 Java 成员中文普通块注释质量门禁可测试。

**先掌握：** 模块化单体与“按 Controller/Service/Repository 全局分包”的差别；依赖倒置如何让领域逻辑不依赖框架。

**Codex 范围：** 建 `forge-server`，配置 Actuator、springdoc-openapi、开发环境 Swagger UI、requestId filter、错误处理、模块包与 ArchUnit 边界测试；Swagger 注释仅允许 Controller 使用。配置自定义 Checkstyle AST/JavaParser 校验的 class 字段、record 组件、enum 常量正反例，确保缺少中文普通块注释（`/* ... */`）时失败。不要建业务 Entity。

**你要检查：** Controller 是否能绕过应用服务访问 Repository；Swagger 是否只列公开 API 而非内部 Agent API；Swagger 注释是否没有进入 Entity/DTO；错误是否泄露堆栈；requestId 是否贯穿响应和日志；质量规则是否同时覆盖普通字段、record 组件与每一个 enum 常量。

**验证：** context-load、health、`/v3/api-docs`、开发环境 Swagger UI、404/validation error、ArchUnit，以及普通字段/record/enum 注释质量门禁负向样例。查看一条 JSON 日志并追踪 requestId。

**复盘问题：** 模块化单体何时优于微服务？为什么 common 不能成为万能垃圾桶？OpenAPI 与 Swagger UI 分别解决什么问题？异常为何要映射稳定错误码？

### 会话 03：MySQL、Redis、Qdrant 与 Flyway 测试基座

**结果：** 本机 Docker Desktop 中的基础设施可启动；Backend 用 Testcontainers 运行真实 MySQL/Redis 集成测试；Flyway 规则固定。

**先掌握：** 事实数据、可重建索引和可丢失缓存的区别；为什么 H2 不能充分替代 MySQL 迁移测试。

**Codex 范围：** 开发本机 Docker Desktop Compose、配置 profile、数据库连接、Flyway 空基线/实例设置表、Testcontainers base、readiness；MySQL/Redis/Qdrant 默认不映射宿主机端口。不要批量创建全部领域表。

**你要检查：** 数据端口是否只位于本机 Docker Compose 内网；真实 Secret 是否未提交；测试是否真的连接 MySQL 8 而不是 H2；命名 Volume 是否能在重启容器后保留数据。

**验证：** 从空卷迁移成功、重复启动无变化、故意破坏 checksum 会失败；Redis/Qdrant 健康检测可区分 ready/degraded。

**复盘问题：** 为什么迁移不可修改？Qdrant 挂掉时文档为何仍应可读？Redis 丢数据的业务后果是什么？

### 会话 04：Web 骨架、设计系统与生成 Client 占位

**结果：** Next.js 工作台外壳可运行，有登录/项目占位路由、错误边界和 API transport；UI 不手写重复 DTO。

**先掌握：** Server state 与 UI state 的不同；由 Swagger/OpenAPI 导出的 Client 如何减少前后端类型漂移；Next.js Server/Client Component 边界。

**Codex 范围：** 建 `forge-web`、Arco provider、layout、TanStack Query、Zustand 空 store、API transport/error mapping、Vitest/Testing Library；同时建立 Feature 目录、公开 `index.ts`、组件/Hook/API/Schema 示例和 ESLint 模块边界规则。不要做真实登录。

**你要检查：** Query Client 是否每次 render 重建；Zustand 是否存了服务端资源；API base URL 与 cookie credentials 是否正确；`page.tsx` 是否只做组合；示例业务是否已拆为组件、Hook 和 API 封装；是否存在万能 `components.tsx/hooks.ts/utils.ts`。

**验证：** lint/typecheck/unit/build；模块边界负向测试；Mock API 错误能显示 requestId；路由 loading/error/empty 状态可见；组件和 Hook 可分别测试。

**复盘问题：** 为什么服务端数据不应放全局 Zustand？页面组件、业务组件和 Hook 的职责如何区分？什么时候应该抽共享组件？生成 Client 的源头是什么？

### 会话 05：Agent 骨架、契约目录与三应用 Smoke

**结果：** FastAPI Agent 服务启动；Backend 可通过内部认证请求健康端点；Tool/Skill 契约目录和校验命令存在。

**先掌握：** Agent Runtime 与业务系统隔离的原因；Contract 是安全边界，不只是函数描述。

**Codex 范围：** 建 `forge-agent`、Pydantic settings、health、空 RuntimeGateway、Tool/Skill schema validator；Compose 串起三应用；CI 执行三端最小检查。

**你要检查：** Agent 是否误连业务 MySQL；内部端点是否暴露公网；依赖是否锁版本；空 Tool Contract 缺风险字段能否被 CI 拒绝。

**验证：** 三应用 Compose smoke；Backend→Agent 内部认证成功/失败；契约负向测试。

**阶段出口：** 新环境执行文档中的命令可启动三应用与三项基础设施；CI 基础链全绿。此时再提交一次架构讲解笔记，不进入业务开发前先画出调用图。

---

## 5. P1：身份、Workspace 与租户隔离（会话 06–10）

### 会话 06：实例初始化与用户密码模型

**结果：** 只有未初始化实例能原子创建首个 Admin、Organization 和 Workspace。

**先掌握：** BCrypt 的 salt/成本；并发初始化竞态；为什么“users 表为空”不是可靠初始化锁。

**Codex 范围：** V1 身份表、`InstanceBootstrapService`、初始化 API、审计；本轮新增的 Entity 字段、record 组件、enum 常量和其他 Java 成员均按中文普通块注释规则标注，并仅在 Controller 补 Swagger 接口说明。只做初始化，不做登录 Session。

**你要检查：** 密码/Hash 是否进日志或响应；两个并发请求是否只有一个成功；失败事务是否留下半套数据。

**验证：** 正常、重复、并发、弱密码、事务回滚测试。

**复盘问题：** BCrypt 为什么不可解密？初始化为什么要事务？唯一约束与应用检查各负责什么？

### 会话 07：登录、Spring Session 与退出

**结果：** 用户可登录、查询 `/me`、退出；Session 存 Redis 并可撤销。

**先掌握：** 服务端 Session 与 JWT 的权衡；Session fixation；认证与授权的差别。

**Codex 范围：** Login Filter/AuthContext、Redis Session、Cookie 安全属性、失败限流、登录审计。暂不做复杂角色授权。

**你要检查：** 登录后 Session ID 是否旋转；错误是否枚举用户；Redis 清除 Session 后是否立即失效。

**验证：** 正确/错误密码、锁定、Session fixation、logout、过期、并发会话测试。

**复盘问题：** 为什么本项目选 Session 而非 JWT？HttpOnly 防什么不防什么？退出为何必须服务端失效？

### 会话 08：CSRF 与 Web 登录闭环

**结果：** Web 完成初始化/登录/退出；所有修改请求携带并验证 CSRF Token。

**先掌握：** Cookie 自动携带为何产生 CSRF；CORS 与 CSRF 不是同一问题；SameSite 不是唯一防线。

**Codex 范围：** CSRF endpoint/transport、Web 表单与 protected layout、错误/加载态；不做 Workspace 业务页面。

**你要检查：** GET 是否意外改状态；Token 是否进 URL/日志；401 与 403/CSRF UI 是否区分。

**验证：** 浏览器正常登录；缺失/错误 Token 修改被拒；跨 Origin 请求被拒；Web component/E2E smoke。

**复盘问题：** XSS 得手后 CSRF Token 是否仍绝对安全？为什么 GET 必须无副作用？SameSite=Lax 的边界是什么？

### 会话 09：Workspace、Project 与成员范围

**结果：** 创建/查看/归档 Project，管理 Workspace/Project Member；访问范围由服务端证明。

**先掌握：** Workspace Member 与 Project Member 的不同职责；tenant scope 必须下沉到查询层。

**Codex 范围：** V2 project/member 表、应用服务、API、基础 Web 页面；暂不分角色细权限。

**你要检查：** Repository 是否存在无 scope 的 `findById` 业务调用；Project key 唯一冲突如何呈现；归档是否等于删除。

**验证：** Workspace A/B 交叉 ID 的读写都返回安全错误；成员移除后项目访问失效；version conflict。

**复盘问题：** 为什么只在 Controller 检查 workspace 不够？404 与 403 如何选择？Project Member 为什么不能代替 Role？

### 会话 10：RBAC、授权点与权限矩阵测试

**结果：** 默认角色种子、Workspace/Project scope 角色、声明式入口 + 应用层最终授权生效。

**先掌握：** RBAC 的 subject/role/permission/resource/action；UI 权限与服务端授权；fail closed。

**Codex 范围：** RBAC 表/种子、PermissionEvaluator、缓存版本、核心 API 授权、权限矩阵集成测试、按钮可见性。不要做自定义角色编辑器。

**你要检查：** Owner 是否也绕过 HIGH 审批（不应）；角色变更后缓存是否失效；Agent 权限是否另开后门（不应）。

**验证：** 每个默认角色一组 allow/deny；项目级角色不泄漏到其他项目；无 Redis 缓存时仍正确；跨租户矩阵全绿。

**阶段出口：** 用两个 Workspace、至少三种角色现场演示允许与拒绝。能从 Web 按钮一路讲到 Application Service 和 scoped Repository。

---

## 6. P2：Product 与 UX 交付核心（会话 11–17）

### 会话 11：Work Item 聚合与原子编号

**结果：** 创建/查询/编辑 Requirement 与 Task；项目内 `PRJ-1` 编号并发安全、永不复用。

**先掌握：** 聚合不变量、数据库锁、乐观锁与悲观锁分别解决什么问题。

**Codex 范围：** V3 work item/sequence 基础表、领域对象、创建/更新 API、列表分页；不做 Transition 和关系。

**你要检查：** item_key 是否由服务端生成；并发创建是否重复；PATCH 是否带 expectedVersion；逻辑删除后编号是否复用。

**验证：** 50 个并发创建唯一；两个并发 PATCH 只有一个成功；type/status 初值匹配；分页 SQL 使用索引。

**复盘问题：** 为什么编号分配用行锁而普通编辑用乐观锁？编号缺口为何可接受？ORM Entity 为何不等于领域模型？

### 会话 12：Requirement 固定状态机与 Guard

**结果：** Requirement 只能通过 Action 转换；产品评审关键 Guard 和活动事件生效。

**先掌握：** 状态、事件、命令的区别；Guard 为什么应确定且无副作用；非法状态组合问题。

**Codex 范围：** Workflow registry、首批 Product Actions、`work_item_events`、Transition API、domain tests；暂不做 UX/Dev/QA 全部 Guard。

**你要检查：** API 是否允许直接传 targetStatus；Guard 失败是否不增 version；事件/审计是否与状态同事务。

**验证：** 状态可达性测试、每个非法 From/Action、缺材料 Guard、并发 Transition、活动时间线。

**复盘问题：** 为什么不用通用工作流引擎？Guard 与权限检查顺序为何重要？事件和当前状态谁是事实源？

### 会话 13：文档不可变版本与 Tiptap 编辑器

**结果：** 创建 PRD、保存版本、发布指定版本、查看历史；Web 编辑器不覆盖并发更新。

**先掌握：** metadata 与 immutable version；content hash；本地草稿与服务端版本的边界。

**Codex 范围：** V4 文档表、Document Service/API、Tiptap、版本列表、发布 Action、基础附件接口可后置。先不做 Qdrant。

**你要检查：** 历史版本能否被修改（不能）；current_version 切换与版本创建事务；本地草稿 key 是否含 baseVersion。

**验证：** 新版本单调递增、并发保存冲突、发布不存在版本失败、旧版仍可读、编辑器刷新恢复草稿。

**复盘问题：** 为什么自动保存不直接生成服务端版本？hash 能解决什么不能解决什么？文档发布与索引为何解耦？

### 会话 14：Product 纵向切片

**结果：** Product 可从页面创建 Requirement、编写/发布 PRD、提交并批准产品评审，进入 UX 阶段。

**先掌握：** 纵向切片如何同时验证 UI/API/领域/DB；后端如何向 UI 返回 Guard hints。

**Codex 范围：** Requirement Detail、PRD tab、Product actions、Activity、对应权限/E2E。不要生成 AI PRD。

**你要检查：** UI 隐藏按钮之外服务端是否拒绝；失败 Guard 是否告诉用户缺什么；刷新后状态是否来自服务端。

**验证：** Product happy path；Developer 只读；无 PRD/未发布/版本冲突；页面刷新和错误 requestId。

**复盘问题：** 为什么先完成纯人工闭环再加 Agent？availableActions 是授权事实吗？纵向切片比按层批量开发好在哪里？

### 会话 15：UX Task、UX 文档与评审

**结果：** UX 队列、UX Task/Spec、提交/退回/批准评审完整，Requirement 可进入 READY_FOR_DEV。

**先掌握：** Requirement 主流程与 UX Task 局部状态机的协同；评审为何冻结交付物版本。

**Codex 范围：** Task 状态机、review_records、UX Workspace/UI、UX Actions/Guards、权限测试；不做跳过 UX。

**你要检查：** UX Task DONE 是否会绕过 Requirement Review；退回后旧 Review 是否仍保留；批准记录是否指向确切文档版本。

**验证：** 正向评审、退回再提交、缺用户流/UX Spec、Product 与 UX 权限边界、并发批准。

**复盘问题：** 为什么 Task DONE 不等于阶段准入？评审记录为什么不可覆盖？交付物版本变化后旧批准是否有效？

### 会话 16：UX 跳过策略、关系与 Activity

**结果：** 合法场景可审计跳过 UX；父子/依赖/关联关系可管理并显示统一活动。

**先掌握：** Policy 与 Permission 的区别；关系表与 parent_id 的职责；审计例外路径的重要性。

**Codex 范围：** project policy、`SKIP_UX`、relations、comments/activity facade；不做完整 Delivery Graph 可视化。

**你要检查：** 有权限但 policy 禁止时是否拒绝；reason 是否必填；跨 Workspace/Project 关系和 self relation 是否拒绝。

**验证：** 后端需求合法跳过、普通需求拒绝、策略关闭、关系重复/自环/跨租户、Activity 顺序。

**复盘问题：** 权限允许为何仍可能不能执行？parent 与 relation 何时各用？审计日志与用户 Activity 有何不同？

### 会话 17：Delivery Graph 与 Product/UX E2E

**结果：** 从 Requirement 展示文档、Task、评审和关系图；Product→UX 黄金前半程成为稳定回归测试。

**先掌握：** 图遍历的环/深度/节点上限；组合查询为何不等于跨模块 Repository 乱调。

**Codex 范围：** DeliveryGraphQuery、响应 DTO、前端图/列表降级、E2E fixtures；不接 GitLab/QA。

**你要检查：** 节点授权是否逐一保证；环是否无限递归；大图是否截断；图查询是否 N+1。

**验证：** 正常图、环、500 节点截断、无权节点、空图；完整 Product/UX E2E。

**阶段出口：** 在没有 Agent 的情况下稳定演示 Requirement → PRD → UX Task/Spec/Review → Ready for Dev，并能解释所有状态转换和失败 Guard。

---

## 7. P3：Agent 基础与 Product/UX Skill（会话 18–22）

### 会话 18：Agent Run、Trace 与 SSE 持久事件

**结果：** Backend 可创建 Run、持久化状态/事件并通过 SSE 重放；Agent 暂用 Fake Runner。

**先掌握：** 任务状态与实时连接解耦；SSE 至少一次投递、sequence reducer 与断线恢复。

**Codex 范围：** V7 Run/Step/Event 表、Run API、Fake dispatcher、SSE endpoint、Web Run 页面/reducer。暂不接 LLM。

**你要检查：** SSE 断开是否取消任务（不应）；Redis 是否被当成事件事实源（不应）；事件正文是否过量。

**验证：** 首连竞态、Last-Event-ID、重复/跳号、断线重连、终态关闭、Run scope 越权。

**复盘问题：** 为什么不用 WebSocket？为什么先查快照再订阅？Reducer 怎样做到重复事件幂等？

### 会话 19：Agent Gateway、Deep Agents 最小图与 Checkpoint

**结果：** Backend 调度真实 FastAPI；Fake LLM 驱动最小图产生计划/完成事件；Checkpoint 可恢复。

**先掌握：** Agent graph 与自由循环的差别；Backend Trace 与 Agent Checkpoint 各自权威范围。

**Codex 范围：** 内部认证、ContextManifest、RuntimeGateway、最小 graph、Fake LLM、checkpoint adapter。不要接业务写 Tool。

**你要检查：** Agent 是否相信 manifest 内权限直接执行；内部端点是否外露；Run 重复调度是否生成两份执行。

**验证：** 正常调度、凭据过期/签名错误、重复 start、节点失败、进程重启恢复。

**复盘问题：** 为什么 Checkpoint 不能代替业务库？Run credential 为何短时？图节点为何要确定边界？

### 会话 20：文档 Outbox、索引与权限过滤 RAG

**结果：** 发布文档异步索引到 Qdrant；Agent 能只检索当前 scope 的片段并引用版本。

**先掌握：** Transactional Outbox、至少一次投递、派生索引、metadata filter 与 prompt filter 的安全差别。

**Codex 范围：** V8 Outbox/index job、Worker、chunker、Embedding Fake/Adapter、Qdrant collection、search facade。先不做混合检索。

**你要检查：** 外部 embedding/Qdrant 调用是否在 DB 事务外；重复事件是否重复 chunk；Workspace filter 是否由 Agent 可移除。

**验证：** 发布→索引、失败重试、重复消费、版本切换、跨 Workspace query、删除失效、Qdrant 故障降级。

**复盘问题：** 为什么 Outbox 比“提交后直接调用”可靠？Qdrant 为何不是事实源？先召回再 Prompt 过滤为什么不安全？

### 会话 21：Tool Registry 与 Product/UX 只读/写 Tool

**结果：** Agent 可读取 Requirement/PRD、生成结构化建议，并通过受控 Tool 创建 UX Task/文档。

**先掌握：** Tool Contract 的 schema/permission/risk/idempotency 四条防线；模型建议与业务执行的区别。

**Codex 范围：** Registry、contract validator、Tool transport、首批 get/search/create Tool、Product/UX Skill、Fake LLM 确定性图测试。MEDIUM 默认先要求确认，可用简化审批 UI。

**你要检查：** Tool 是否有内部 API 绕过应用服务；模型提供 workspaceId 是否被信任；重复 Tool 是否只创建一次。

**验证：** 合法 Tool、Schema 错误、Skill 禁用 Tool、权限撤回、跨 scope 参数、幂等重放、结果结构化。

**复盘问题：** JSON Schema 为什么不是授权？Agent 说“成功”与 Backend 返回成功哪个可信？Tool 为什么要版本化？

### 会话 22：正式审批、暂停/恢复与 Product/UX Agent E2E

**结果：** MEDIUM 策略可配置确认；审批冻结参数和资源版本；批准后从 checkpoint 恢复且不重复写。

**先掌握：** Human-in-the-loop 不是一个确认弹窗，而是一段可恢复分布式状态机；TOCTOU 风险。

**Codex 范围：** approvals 表/服务、Web 卡片、WAITING_APPROVAL、resume Outbox、过期/拒绝/资源变化测试、Agent 前半程 E2E。

**你要检查：** 审批后是否直接执行旧参数而不重验；同一用户是否不当自批；浏览器关闭后审批是否还存在。

**验证：** approve/reject/expire/cancel、参数 hash 变化、资源 version 变化、权限撤回、恢复进程重启、幂等成功重放。

**阶段出口：** Agent 在真实权限与文档范围内完成“读取 PRD → 计划 → 请求确认 → 创建 UX Task/Spec”，SSE 和 Trace 可重放；越权与过期审批明确拒绝。

---

## 8. P4：Development 与 GitLab（会话 23–27）

### 会话 23：SecretService 与 GitLab 连接

**结果：** Admin 可安全保存/轮换 GitLab Token、测试 GitLab.com 或私有 Base URL，并绑定 Repository。

**先掌握：** 信封加密与 Hash 的不同；SSRF；外部凭据的最小权限与轮换。

**Codex 范围：** V5 secrets/connection/repository 表、AES-GCM SecretService、GitLab test/repo read、设置 UI、WireMock。不要做 Branch/MR。

**你要检查：** Token 是否在 API/日志/异常/Agent 中出现；Base URL 重定向与私网规则；主密钥缺失是否 fail fast。

**验证：** 加解密/密文篡改、Token 轮换、401/403/timeout、SSRF metadata/redirect、连接 scope 越权、UI 不回显。

**复盘问题：** 为什么密码用 Hash 而 Token 要加密？SSRF 如何访问云元数据？主密钥丢失为何不能靠数据库恢复？

### 会话 24：Dev Task、Branch 与 MR Adapter

**结果：** 从 READY_FOR_DEV 创建 Dev Task、启动开发、创建/关联 Branch 和 MR。

**先掌握：** Adapter/SPI 隔离外部系统；远端系统与本地缓存的事实边界；外部写幂等。

**Codex 范围：** Branch/MR 表、GitLab Adapter、应用服务/API/UI、START_DEVELOPMENT；暂不处理 Pipeline/Webhook。

**你要检查：** GitLab DTO 是否渗入核心领域；同名 Branch 不同 SHA 如何处理；MR timeout 后是否盲目重试。

**验证：** 正常创建、已有同基准 Branch/MR 复用、冲突、429/timeout reconcile、无权限、Work Item 关联。

**复盘问题：** Adapter 与简单 HTTP Client 有何差别？为什么本地不能宣称 MR 的最终状态？外部 API 不支持幂等时怎么办？

### 会话 25：Pipeline、Webhook 与 Outbox 处理

**结果：** 触发/查看 Pipeline；签名 Webhook 异步同步 MR/Pipeline，重复/乱序可恢复。

**先掌握：** Webhook 验证、快速确认、至少一次事件、upsert 与远端时间版本。

**Codex 范围：** pipeline/webhook 表、trigger/log tail、Webhook Controller/Processor、Development UI 实时状态。不要推进 QA Guard。

**你要检查：** Controller 是否同步做大量处理；payload 是否无限保存；重复 delivery 是否产生重复状态事件；乱序旧事件是否覆盖新状态。

**验证：** 合法/非法签名、重复 delivery、乱序、未知事件、超大 payload、Pipeline success/fail/cancel、日志截断。

**复盘问题：** 为什么 Webhook 先 202 后处理？如何定义 delivery key？Webhook 与定时 reconcile 为什么都需要？

### 会话 26：Development → QA Guard 与开发页面闭环

**结果：** Dev Task 完成、MR/CI 满足策略后 Requirement 可进入 READY_FOR_QA；失败时给出明确缺项。

**先掌握：** 外部状态同步与内部工作流推进解耦；策略快照与实时检查；stale 数据问题。

**Codex 范围：** `SUBMIT_FOR_QA` Guards、Development 汇总 Query/UI、Project CI policy、集成/E2E；不做 Test Case。

**你要检查：** Pipeline 成功是否对应 MR 当前 head SHA；过期缓存是否误放行；无 GitLab 项目按 policy 是否有清晰行为。

**验证：** 缺 Dev Task、缺 MR、失败/运行中/旧 commit Pipeline、CI optional、并发提交、Webhook 后重试。

**复盘问题：** “有一个成功 Pipeline”为什么不够？项目策略与硬编码规则如何分工？为什么 Webhook 不直接改 Work Item 状态？

### 会话 27：Developer Skill 与 GitLab 故障恢复

**结果：** Developer Agent 基于 PRD + UX 生成 Tech Design/Dev Tasks，并在确认后创建 Branch/MR、解释 CI；故障不会重复写。

**先掌握：** Skill 最小上下文；外部工具结果的结构化解释；重试、reconcile 与人工恢复边界。

**Codex 范围：** Developer context template/Tools/Skill、CI log 摘要限制、Agent Eval Cases、GitLab reconciliation job。不要让 Agent 改代码或 merge。

**你要检查：** Skill 是否可调用 deploy/QA 写 Tool（不应）；CI log 是否含 Secret/超大正文；失败解释是否区分事实与建议。

**验证：** 正向 Dev Agent、创建确认、GitLab 429/timeout、Branch 已存在、失败 Pipeline 解释、禁用 Tool、跨 Project 检索。

**阶段出口：** 人工与 Agent 两条路径都能从 READY_FOR_DEV 走到 READY_FOR_QA；GitLab 不可用时不丢业务数据、不重复远端资源，也不错误推进状态。

---

## 9. P5：QA、Release 与 HIGH 审批（会话 28–31）

### 会话 28：Test Case、Test Run 与 QA Guard

**结果：** QA 创建/执行用例，统计结果，Requirement 从 READY_FOR_QA 进入 IN_QA，再 PASS 到 READY_FOR_RELEASE。

**先掌握：** Test Case（设计）与 Test Result（一次执行事实）；不可变完成统计；确定性 QA Guard。

**Codex 范围：** V6 test tables、服务/API/QA UI、START_QA/QA_PASS；先不做 Bug 回流。

**你要检查：** 修改已完成 Run 是否需 reopen；SKIPPED 如何按优先级处理；Agent 是否能替代 QA 放行（不能）。

**验证：** 全 PASS、FAIL/BLOCKED/必测 SKIPPED、结果并发更新、Run reopen、无权执行、统计一致性。

**复盘问题：** 为什么 Test Result 要属于 Run？完成后的结果为何不能静默修改？QA 结论为何必须由代码计算？

### 会话 29：Bug 状态机、开发回流与 QA Agent

**结果：** 失败结果可创建关联 Bug；QA_FAIL 回到开发；修复、验证、重新回归闭环；Agent 可生成用例/Bug 草稿。

**先掌握：** Bug 与普通 Task 的生命周期差异；缺陷关系如何形成追踪；AI 草稿与人工测试事实。

**Codex 范围：** bug_details/状态机、关系、QA_FAIL、QA Skill/Tools/Eval、UI。不要做 Release。

**你要检查：** Developer 是否能自行 VERIFY（默认不能）；Bug RESOLVE 是否有关联修复证据；Agent 是否能把未经执行的用例标 PASS。

**验证：** fail→bug→fix→verify→retest、reopen、BLOCKER 阻断、权限、重复 Agent create_bug、Delivery Graph 节点。

**复盘问题：** RESOLVED 与 VERIFIED 为什么分开？Bug 如何关联 Requirement/MR/Test？Agent 在 QA 中最适合做什么、不适合做什么？

### 会话 30：Release、确定性 Precheck 与 Release Note

**结果：** 创建 Release Candidate，聚合 Work Item/CI/QA/Bug，运行不可变 Precheck，并生成/编辑 Release Note。

**先掌握：** Precheck 快照与执行时再验证；发布聚合为何独立于 Work Item；代码规则与 Agent 解释的边界。

**Codex 范围：** release/precheck/items 表、Precheck rule registry、Release UI、Release Agent 只读 precheck + note Tool。暂不执行 Deployment。

**你要检查：** Agent 是否决定 PASS（不应）；检查是否针对 MR head/latest valid Test Run；重新 QA 后旧 Precheck 是否失效。

**验证：** 六项检查各一组通过/失败、资源版本变化、重复预检、Release version 唯一、Release Note 引用。

**复盘问题：** 为什么 Release 不直接复用 RELEASE Work Item？快照有什么价值？Precheck 与审批的职责如何不同？

### 会话 31：HIGH 审批、模拟部署与完整后半程 E2E

**结果：** Deploy 必须 HIGH 审批；冻结/过期/自批策略生效；模拟部署成功后 Requirement RELEASED/DONE。

**先掌握：** 双人控制、自审批例外、HIGH 操作不自动重试、Deployment Record 与真实部署区别。

**Codex 范围：** Deployment service/worker、HIGH approval policy、Release Skill deploy Tool、UI 与 QA→Release E2E。只做清楚标记的 SIMULATED 模式。

**你要检查：** Owner 是否未经批准部署；批准后 Release 变化是否仍执行；失败 HIGH Tool 是否由 Agent 自动重试（不应）；UI 是否误称生产成功。

**验证：** 未批/批准/拒绝/过期/自批/资源变化、重复点击、模拟成功/失败、Requirement 状态联动、Audit/Trace。

**阶段出口：** 人工和 Agent 均能完成 QA→Bug 回流→Precheck→Approval→Simulated Deployment；所有发布放行依据可从 UI 和 Trace 解释。

---

## 10. P6：完整交付、加固与面试准备（会话 32–34）

### 会话 32：黄金 Demo 数据与端到端回归

**结果：** “手机号验证码登录”可一键装载确定性样例，并完整跑通正向闭环；关键负向路径自动化。

**先掌握：** E2E 的价值是验证系统接缝，不替代底层测试；Fixture 可重复性与测试隔离。

**Codex 范围：** Demo seed/Mock LLM/Mock GitLab、Playwright 黄金链、Delivery Graph/Agent Trace 最终视图、测试文档。不要为了演示硬编码业务结果。

**你要检查：** Seed 重复执行是否安全；Mock 与真实 Adapter 是否走同一 SPI；测试失败能定位到哪个阶段。

**验证：** 空环境装载、正向全链、九个负向场景、重复运行、截图/录像脚本可选。

**复盘问题：** 哪些部分是确定性、哪些依赖模型？E2E 为什么不能只断言页面有文字？Mock 如何避免和真实协议漂移？

### 会话 33：安全、可靠性、性能与故障演练

**结果：** 跨租户、CSRF、SSRF、Secret、幂等、并发、SSE、Outbox 和故障降级有证据；关键性能预算可度量。

**先掌握：** 威胁建模、fail closed、故障注入、性能预算与“提前优化”的区别。

**Codex 范围：** 安全负向测试集、故障注入、10k Work Item 数据、关键查询 explain/索引、日志脱敏扫描、指标面板/文档。只修复实际发现的问题。

**你要检查：** 安全测试是否只是 mock PermissionEvaluator；压测是否包含真实分页/索引；故障是否可能造成误放行。

**验证：** 详细设计 20.2/20.3 清单；DB/Redis/Qdrant/Agent/GitLab 单项故障；P95 报告；Secret grep/日志检查。

**复盘问题：** 哪三处是系统最重要的 fail-closed 点？最可能的数据一致性故障是什么？哪个性能瓶颈最先出现、为何？

### 会话 34：本机 Compose、腾讯云上线与面试叙事

**结果：** 本机 Docker Desktop 可完成开发/Demo 启动、备份、恢复与升级 Smoke；腾讯云 CVM 可完成最终上线部署、HTTPS 与运行验证；README 和架构讲解完整。

**先掌握：** 本地开发环境与生产上线环境应保持服务语义一致但隔离配置；备份不等于恢复；面试讲解应呈现取舍和证据而非堆技术名词。

**Codex 范围：** 本机 Docker Desktop Compose、腾讯云生产 Compose/Nginx/TLS、环境专用 `.env.example`、安装/备份/恢复/升级脚本与文档、SBOM、最终 ADR/架构图、Demo runbook。不要代替你编造性能或安全结论。

**你要检查：** 本机数据端口是否无意映射；腾讯云数据端口是否未公网暴露；SSE buffering；主密钥备份说明；从完全空 Docker Desktop 和腾讯云 CVM 按 README/Runbook 能否成功；所有宣称是否有测试/测量证据。

**验证：** 全新 Docker Desktop 本机环境 dry run、实际本地备份恢复、腾讯云上线 Smoke、镜像 SHA/Flyway 版本、Compose smoke、最终 CI、Demo runbook 计时。

**最终出口：** 你能在 10 分钟内完成产品 Demo，在 15 分钟内画出架构与信任边界，并对状态机、RBAC、Outbox、SSE、Tool/Approval、RAG 过滤各回答一个深挖问题。

---

## 11. 每阶段建议安排一次“只讲不改”的复盘会话

这类会话不计入 34 个主开发会话，尤其适合面试项目。让 Codex 基于当前真实代码做代码走读，但禁止改文件。

推荐 Prompt：

```text
这是 ForgeAI 的阶段复盘，不修改任何文件。
请基于当前代码完成：
1. 从一个真实请求入口追踪到数据库/外部系统，列出具体类和方法。
2. 指出本阶段三个最重要的不变量，以及分别由哪段代码/约束/测试保护。
3. 设计三个面试官可能追问的故障或攻击场景，先让我回答，再评价。
4. 找出一个可以改进但不应现在过度设计的点，解释判断依据。
不要泛泛复述技术栈，也不要直接给出所有问题答案。
```

建议复盘主题：

- P0：模块边界、契约生成、环境一致性。
- P1：Session、CSRF、RBAC、租户隔离。
- P2：聚合、乐观锁、固定状态机、不可变版本。
- P3：SSE、Outbox、RAG scope、Tool 与 checkpoint。
- P4：Adapter、Secret、Webhook、最终一致性。
- P5：确定性质量门、审批冻结、幂等发布。
- P6：故障降级、部署、证据化表达。

---

## 12. 每轮学习卡模板

把下列内容提交到 `docs/learning/session-XX.md`，每篇控制在 1–2 页，使用你自己的表达。Codex 可以提出问题和校对，但不要让它代写全部答案。

```markdown
# Session XX：主题

## 我完成了什么
- 用户可见结果：
- 从入口到结果的调用链：

## 我理解的核心设计
- 关键不变量：
- 事务/一致性边界：
- 权限与安全边界：
- 为什么没有选择另一种方案：

## 失败路径
- 触发方式：
- 系统如何失败：
- 数据是否保持正确：
- 如何定位与恢复：

## 测试证据
- 测试名与断言：
- 它不能证明什么：

## 仍不清楚的问题
- ...
```

这组学习卡最终会自然转化成面试讲解材料，也能避免“代码是 AI 写的、自己只知道表面”的风险。

---

## 13. 控制范围的删减顺序

若时间不足，按以下顺序缩减，不破坏主闭环：

1. 先减少 UI 美化、复杂统计和 Graph 动画，保留清楚可用的列表/图。
2. RAG 先只做 dense retrieval，不做 hybrid/rerank，但租户过滤与版本引用不能删。
3. GitLab 先支持单仓库、核心 Branch/MR/Pipeline，不做完整日志浏览与多仓库。
4. 角色只保留默认种子，不做自定义角色编辑器。
5. Release 只保留 SIMULATED Deployment，但 HIGH Approval、Precheck 和审计不能删。
6. Agent 先保证 Product/UX + 一个 Developer + 一个 QA/Release 黄金 Skill，不追求通用自然语言能力。

绝不能为赶进度删掉：Workspace 隔离、服务端授权、状态机 Guard、乐观锁、Tool Contract、幂等、审批冻结、Agent Trace、关键负向测试。

---

## 14. 面试前最终自检

### 14.1 你应该能够现场解释

- 为什么是模块化单体 + 独立 Agent，而不是全微服务或全 Python。
- 为什么 MySQL、Redis、Qdrant 三者不能互换事实职责。
- 一次 Work Item Transition 的权限、Guard、事务、事件和审计链路。
- 文档发布到 RAG 可检索之间的最终一致性与失败恢复。
- SSE 断线为何不影响 Run，以及如何避免漏/重事件。
- Agent Tool 从模型选择到 Backend 执行经历哪些校验。
- Approval 如何防止批准后参数被替换或资源已变化。
- GitLab Webhook 重复、乱序和漏事件如何处理。
- 为什么 Agent 能生成 QA/Release 建议，却不能决定质量放行。

### 14.2 你应该能现场展示证据

- 一个跨 Workspace 请求被拒绝的集成测试。
- 一个并发 version conflict 或原子编号测试。
- 一个 Tool 未审批/过期审批不执行的测试。
- 一次 SSE 断线重放。
- 一次重复 Webhook/幂等 Tool 只产生一个副作用。
- 一次 Qdrant 跨租户过滤负向测试。
- 一次完整黄金 Demo 的 Trace 与 Delivery Graph。

### 14.3 遇到不会的问题时的正确表达

不要把尚未实现的扩展说成现有能力。说明当前边界、为什么 MVP 如此取舍、已保留什么扩展点、需要什么证据才会升级设计。对面试项目而言，诚实且能解释取舍，比罗列未验证的高级组件更有说服力。
