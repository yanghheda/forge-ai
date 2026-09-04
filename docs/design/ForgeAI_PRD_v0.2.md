# ForgeAI 产品需求文档（PRD）v0.2

> **AI-Native Software Delivery Workbench**

| 属性 | 内容 |
|---|---|
| 文档版本 | v0.2（ForgeAI 品牌与 UX 流程更新版） |
| 文档状态 | MVP 开发前产品基线 |
| 产品名 | ForgeAI |
| 产品形态 | 开源、自托管、单实例私有部署 |
| 目标用户 | 个人开发者、小团队、小公司内部研发团队 |
| 核心闭环 | Product → UX → Development → QA → Release |
| 仓库名 | `forge-ai` |
| GitHub | `forge-ai/forge` |
| 核心模块 | `forge-web`、`forge-server`、`forge-agent` |
| 配套技术基线 | 《ForgeAI 完整技术方案 v1.3》 |

本 PRD 是 ForgeAI 后续产品设计、技术设计、开发计划和验收测试的基线。产品范围刻意收窄，以保证单人可以完成并上线，同时确保多人团队可以围绕真实交付流程协作。

---

## 1. 文档目的与产品结论

ForgeAI 是一个开源、自托管、AI 原生的软件交付工作台。它将需求、PRD、UX 设计、技术文档、任务、GitLab、CI、测试、缺陷和发布组织为统一的交付链，并通过 AI Agent 理解研发上下文、制定计划、调用受控 Tool、执行操作，在敏感节点请求人工确认。

ForgeAI 不替代 Jira、Confluence、Figma、GitLab 或开发者 IDE，而是在这些角色与系统之间提供一层统一的研发上下文和 AI 自动化能力。

本项目采用自托管模式：个人、团队或公司独立部署私有实例，数据、模型 API Key、GitLab Token 和外部服务配置由部署方掌控。ForgeAI 官方不要求中心化账号服务，也不统一托管不同公司的业务数据。

开发者继续使用自己的 IDE、CLI 或 AI Coding 工具编码。ForgeAI 负责需求与文档、任务、UX 交付、分支/MR/CI 状态、测试、缺陷、发布，以及 Agent 对这些环节的编排。

### 1.1 README 产品定位

> ForgeAI is an open-source, self-hosted AI-native software delivery workbench. It connects Product, UX, Development, QA and Release workflows with AI Agents, enabling teams to manage requirements, documents, code collaboration, CI/CD and delivery automation in one workspace.

---

## 2. 产品定位

### 2.1 一句话定位

> 让个人与小团队在一个私有、自托管的研发工作台中，使用 AI Agent 将 Product、UX、Development、QA 和 Release 之间的机械操作自动化。

### 2.2 核心价值

- 以 Work Item 为核心，连接 PRD、UX、技术方案、Task、MR、CI、Test、Bug 和 Release。
- 以固定交付 Workflow 为骨架，使 Product → UX → Development → QA → Release 的责任交接清晰、可追踪。
- 以 Agent 为智能执行层，通过自然语言完成分析、生成、创建、查询、触发、验证和汇总。
- 以 `forge-server` 为业务权威，Agent 只能通过受控 Tool/Java API 操作业务，不能直接修改数据库。
- 以 GitLab 与 GitLab CI 为代码和流水线执行系统，开发者继续在自己的开发环境中编码。
- 以开源、自托管为前提，实例部署方拥有数据、密钥和集成配置。
- 通过 Delivery Graph 让每个需求都能回溯到设计、开发、测试与发布结果。

### 2.3 产品原则

1. 团队模式是主模型；个人模式是只有一个成员的 Workspace。
2. UX 是正式角色与交付阶段，不是开发任务的附属标签。
3. Agent 负责理解、规划、检索、编排和解释；业务规则、权限、事务与持久化属于 Java。
4. API First；外部系统有 API 时不使用浏览器自动化。
5. 所有 Agent 写操作受 Tool Contract、权限、风险策略和审计约束。
6. MVP 以模块化单体和 Docker Compose 控制复杂度。

---

## 3. 目标用户与部署模式

### 3.1 目标用户

| 用户类型 | 典型规模 | 主要诉求 | ForgeAI 定位 |
|---|---:|---|---|
| 个人开发者 | 1 人 | 管理个人 AI 辅助研发流程，沉淀需求、UX、技术、测试和发布信息 | 单成员 Workspace |
| 小团队 | 2–10 人 | Product、UX、Developer、QA 之间的任务流转和上下文共享 | 核心目标用户 |
| 小公司/研发组 | 5–30 人 | 私有部署、内部数据隔离、统一流程和 Agent 自动化 | 主要扩展场景 |

### 3.2 组织模型

统一采用：

```text
Organization
  └── Workspace
      ├── Members
      └── Projects
```

MVP 可以弱化 Organization 的管理能力，但数据模型保留该层级，不为个人版和团队版设计两套产品。

### 3.3 部署模式

| 部署项 | 原则 | MVP 要求 |
|---|---|---|
| 业务数据 | 实例私有 | 本地开发使用 Docker Desktop 中的 MySQL 8；上线部署在腾讯云私有网络中 |
| 会话/缓存 | 实例私有 | 本地开发使用 Docker Desktop 中的 Redis；上线部署在腾讯云私有网络中 |
| 向量检索 | 实例私有 | 本地开发使用 Docker Desktop 中的 Qdrant；上线部署在腾讯云私有网络中 |
| 大模型 | 部署方配置 | Provider、Endpoint、Model、API Key 可配置 |
| GitLab | 部署方接入 | 支持 GitLab.com 和私有 GitLab API |
| CI/CD | 部署方接入 | GitLab CI 为主，平台触发、读取和解释状态 |
| 开发环境 | 开发者本机 | Docker Desktop + Docker Compose；数据库、缓存和向量库均在本机容器运行 |
| 最终上线环境 | 腾讯云 CVM | Docker Compose + Nginx/TLS；数据服务不暴露公网端口 |

ForgeAI 官方源码仓库为 `forge-ai/forge`；源码托管平台与 ForgeAI 对接用户 GitLab 的产品能力互相独立。

---

## 4. 产品目标与非目标

### 4.1 MVP 产品目标

1. 一个真实需求可以从创建、产品评审、UX 设计、开发、测试流转到可发布状态。
2. Product、UX、Developer、QA 围绕同一个 Work Item 共享 PRD、UX Spec、技术文档、任务、MR、CI、测试和 Bug 上下文。
3. Agent 能读取当前权限范围内的上下文，并通过受控 Tool 执行核心业务操作。
4. GitLab 是代码、Branch、MR 和 Pipeline 的执行系统；ForgeAI 不重做 IDE。
5. 敏感和高风险操作具备权限校验、人工审批、幂等保护和审计。
6. 系统可由单人维护：本地通过 Docker Desktop + Docker Compose 一键启动，最终可部署到腾讯云 CVM。
7. 从 Requirement 可查看完整 Delivery Graph。

### 4.2 明确非目标

- 不做 Jira/Linear、Notion/Confluence、Figma/FigJam 或 GitLab 的完整替代品。
- 不做在线 IDE，不承担开发者日常写代码。
- 不做完整 CI/CD 平台、Kubernetes 管理平台或业务微服务集群。
- 不做实时多人文档编辑、即时通讯、完整通知中心和移动端。
- 不在 MVP 中实现通用 Browser Agent、复杂可配置 Workflow 或企业 SSO。
- 不要求 MVP 完成真实生产部署；必须保留 Release、Precheck、Approval 与 Deployment 扩展模型。
- 不允许 Agent 未经审查直接大规模修改代码并推送受保护分支。

---

## 5. 角色与核心工作方式

| 角色 | 核心职责 | 主要操作 | Agent 典型能力 |
|---|---|---|---|
| Owner/Admin | 实例与 Workspace 管理 | 成员、权限、模型、GitLab、环境、审计 | 检查配置、查看系统状态、审批高风险操作 |
| Product | 业务目标、需求和 PRD | 创建需求、PRD、验收标准、优先级和进度 | 生成/完善 PRD、拆解需求、分析阻塞、汇总进度 |
| UX | 用户体验和设计交付 | 用户流程、页面清单、交互、原型链接、设计规范 | 分析 PRD、创建 UX Task、生成 UX Spec 与评审清单 |
| Developer | 技术实现和代码交付 | 技术方案、Dev Task、Branch、MR、CI | 读取 PRD + UX、生成技术方案、创建 Branch/MR、解释 CI |
| QA | 测试和缺陷管理 | Test Case、Test Run、Bug、回归 | 根据 PRD + UX + API/MR 生成用例、创建 Bug、质量汇总 |
| Release Approver | 发布检查和审批 | Release Candidate、Precheck、Approval、Deployment | 汇总 CI/QA/Bug、生成发布说明、请求人工审批 |

角色是默认权限模板，不等于完全独立的 Agent。系统采用“统一 Agent Runtime + Role Context + Skill + Tool Allowlist + Permission”的模式。一个用户可以拥有多个角色。

---

## 6. 核心业务对象

| 对象 | 说明 | 是否核心 |
|---|---|:---:|
| Organization/Workspace | 私有实例内的组织和工作区边界 | 是 |
| Project | 一个具体研发项目 | 是 |
| Work Item | 统一承载 Requirement、UX Task、Dev Task、QA Task、Bug、Release | 是 |
| Work Item Relation | 表达父子、依赖、阻塞、验证和交付关系 | 是 |
| Document | PRD、UX、技术、API、测试、发布文档 | 是 |
| Repository | GitLab 仓库连接 | 是 |
| Merge Request | GitLab 开发交付对象 | 是 |
| Pipeline | GitLab CI 执行记录 | 是 |
| Test Case/Test Run | 测试设计与执行记录 | 是 |
| Release/Deployment | 版本、环境和部署状态 | MVP 简化 |
| Agent Run/Step/Tool Call | Agent 执行轨迹 | 是 |
| Approval | 中高风险操作审批 | 是 |
| Audit Log | 关键业务与安全审计 | 是 |

### 6.1 Work Item 类型

统一使用 `work_items`，通过 `type` 区分：

```text
REQUIREMENT
UX_TASK
DEV_TASK
QA_TASK
BUG
RELEASE
```

### 6.2 文档类型

```text
PRD
UX_SPEC
PROTOTYPE_SPEC
DESIGN_GUIDE
TECH_DESIGN
API_DOC
TEST_PLAN
TEST_REPORT
RELEASE_NOTE
GENERAL
```

### 6.3 Delivery Graph

```text
Requirement
  ├── PRD
  ├── UX Task
  │   ├── UX Spec
  │   ├── User Flow
  │   └── Prototype / Design Guide
  ├── Tech Design
  ├── Dev Tasks
  │   └── Branch → MR → Pipeline
  ├── QA Task → Test Cases → Test Run
  │                          └── Bug → Dev 回流
  └── Release → Deployment
```

---

## 7. 核心用户旅程

### 7.1 端到端需求交付

1. Product 创建“手机号登录”Requirement。
2. Agent 基于项目上下文生成 PRD；Product 确认目标、范围与验收标准。
3. Requirement 进入 UX 阶段；UX Agent 创建 UX Task，并生成用户流程、页面清单、关键交互和异常状态。
4. UX 上传/关联原型，完善 UX Spec 和 Design Guide；Product/UX 评审通过后进入 Ready for Development。
5. Developer Agent 基于 PRD + UX 文档生成技术方案与 Dev Tasks；负责人确认并分配。
6. Developer 在自己的 IDE/CLI 中编码；ForgeAI 不承载代码编辑。
7. Developer 或 Agent 创建 Branch、关联 Work Item、创建 MR 并触发 CI。
8. CI 状态同步到 ForgeAI，Work Item 展示 MR、Pipeline 和日志摘要。
9. QA Agent 根据 PRD、UX、技术/API 文档和 MR 生成测试用例。
10. QA 执行测试；失败时创建关联 Requirement/Task/MR/Test 的 Bug，并回流 Developer。
11. CI、QA 和缺陷条件满足后进入 Ready for Release；Agent 执行发布前检查并生成 Release Candidate。
12. HIGH 风险发布动作必须 Human Approval；系统记录 Agent Trace、Approval 与 Audit Log。

### 7.2 UX 可跳过场景

纯后端、运维或内部技术任务可由有权用户提交跳过 UX 的理由。系统根据 Project Policy 决定是否允许；跳过必须记录操作者、原因和时间，不能静默绕过。

---

## 8. Workflow 与状态机

### 8.1 主状态流

```text
DRAFT
  → PRODUCT_REVIEW
  → UX_IN_PROGRESS
  → UX_REVIEW
  → READY_FOR_DEV
  → IN_DEVELOPMENT
  → READY_FOR_QA
  → IN_QA
  → READY_FOR_RELEASE
  → RELEASED
  → DONE
```

异常分支包括 `BLOCKED`、`REJECTED`、`CANCELLED`；QA 失败或 Bug 修复使需求回到 `IN_DEVELOPMENT`。

### 8.2 阶段准入

| 转换 | 最低条件 |
|---|---|
| Product → UX | PRD、目标、范围、验收标准完成 |
| UX → Development | UX Spec、用户流程、页面/交互已评审，或有可审计跳过理由 |
| Development → QA | Dev Task 完成，MR/构建可用，CI 达到项目策略 |
| QA → Release | 测试完成，无阻断缺陷，QA 结论明确 |
| Release → Released | Precheck 通过，人工审批有效，部署结果已记录 |

### 8.3 状态迁移原则

- 状态迁移必须由明确动作触发，记录操作者、时间、前后状态和原因。
- Agent 可以建议或发起迁移，但由 `forge-server` 统一校验权限和准入条件。
- HIGH 风险迁移必须人工审批。
- MVP 采用固定状态机，不引入复杂通用工作流引擎。
- 并发更新使用版本号，禁止静默覆盖。

---

## 9. 功能需求

### 9.1 登录、初始化与配置

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| AUTH-001 | 支持首个管理员初始化；后续成员由管理员邀请/创建 | P0 | 首次启动可完成 Admin 初始化并进入 Workspace |
| AUTH-002 | 支持登录、退出和会话管理 | P0 | 使用 Cookie + Spring Session Redis；退出后会话失效 |
| AUTH-003 | 密码安全保存 | P0 | 使用 BCrypt；日志不得记录密码或 Hash |
| CFG-001 | 支持实例级大模型配置 | P0 | 可配置 Provider、Endpoint、Model、API Key |
| CFG-002 | 支持 GitLab 连接配置 | P0 | Workspace 可配置 GitLab.com/私有 GitLab并测试连接 |
| CFG-003 | Secret 安全存储 | P0 | Token/Key 加密，前端与 Agent 不获取明文 |

### 9.2 Workspace、Project 与成员

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| ORG-001 | 创建/编辑 Workspace | P0 | Workspace 可命名并保存设置 |
| ORG-002 | 成员管理与角色分配 | P0 | 可分配 Product/UX/Developer/QA/Approver 等角色 |
| ORG-003 | Workspace 数据隔离 | P0 | 用户不能访问无权限 Workspace 的任何资源或 RAG 内容 |
| PROJ-001 | 创建/编辑/归档 Project | P0 | Project 有名称、Key、描述、仓库和状态 |
| PROJ-002 | Project 成员与角色范围 | P0 | 支持项目级成员范围 |
| PROJ-003 | Project Overview | P0 | 展示需求、UX、开发、质量、CI 和发布概览 |

### 9.3 Requirement 与 Work Item

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| WI-001 | 创建 Requirement | P0 | 支持标题、描述、优先级、负责人和日期 |
| WI-002 | 创建 UX/Dev/QA Task 与 Bug | P0 | 使用统一 Work Item 类型 |
| WI-003 | Work Item 关联 | P0 | 可关联文档、Task、MR、CI、Test、Bug、Release |
| WI-004 | 严格状态流转 | P0 | 按权限、准入条件和版本号迁移 |
| WI-005 | Work Item Activity | P0 | 展示状态、评论、审批和 Agent 执行摘要 |
| WI-006 | Delivery Graph | P0 | 从 Requirement 查看端到端交付关系 |
| WI-007 | Agent 拆分角色任务 | P0 | 可分别生成 UX、Dev、QA Task，审批后创建 |

### 9.4 文档

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| DOC-001 | 富文本编辑器 | P0 | 支持标题、段落、列表、代码块和表格 |
| DOC-002 | 完整文档类型 | P0 | 支持 PRD、UX、技术、API、测试和发布文档 |
| DOC-003 | 文档关联 | P0 | 文档可关联 Project 和 Work Item |
| DOC-004 | 文档版本 | P1 | 保留不可变历史版本并可查看 |
| DOC-005 | AI 文档生成 | P0 | Agent 可基于权限范围内上下文生成/补全草稿 |
| DOC-006 | 附件与外链 | P0 | UX 原型可作为附件或 URL 关联 |
| DOC-007 | 文档索引 | P0 | 发布版本可进入 Qdrant；检索受 Workspace/Project 权限过滤 |

### 9.5 UX 工作区

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| UX-001 | UX Task 队列 | P0 | UX 可查看待设计、评审和阻塞任务 |
| UX-002 | AI 生成 UX 交付草稿 | P0 | 基于 PRD 生成用户流程、页面清单和交互说明 |
| UX-003 | UX 文档管理 | P0 | 支持 UX_SPEC、PROTOTYPE_SPEC、DESIGN_GUIDE |
| UX-004 | UX Review | P0 | Product/UX 可通过或退回，记录意见与历史 |
| UX-005 | 阶段准入 | P0 | 缺少 UX 交付物时不能静默进入开发 |
| UX-006 | 跳过 UX | P1 | 按策略提交理由并完整留痕 |

### 9.6 Task 与看板

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| TASK-001 | 创建不同角色 Task | P0 | Task 有类型、标题、描述、负责人、优先级和截止时间 |
| TASK-002 | Task 追溯来源 | P0 | UX/Dev/QA Task 可追溯 Requirement |
| TASK-003 | Task Board | P0 | 支持按角色/类型/状态筛选的基础看板 |
| TASK-004 | 任务分配 | P0 | 有权角色可分配给成员 |
| TASK-005 | Agent 创建任务 | P0 | Agent 生成建议，按风险策略确认后创建 |

### 9.7 GitLab 与开发上下文

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| GIT-001 | 连接 GitLab | P0 | 支持 GitLab.com 和私有 Base URL |
| GIT-002 | Repository 关联 Project | P0 | 项目可绑定主仓库 |
| GIT-003 | Branch 查看/创建 | P0 | 可查看并按 Work Item 创建 Branch |
| GIT-004 | Merge Request | P0 | 可查看、创建并关联 Work Item |
| GIT-005 | Pipeline | P0 | 可查看状态、触发 Pipeline、读取日志摘要 |
| GIT-006 | Webhook 同步 | P0 | MR/CI 状态可同步，重复事件幂等 |
| GIT-007 | 开发不在平台编码 | P0 | ForgeAI 不提供 IDE，只提供交付上下文 |

### 9.8 QA

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| QA-001 | Test Case | P0 | 支持前置条件、步骤、预期结果和优先级 |
| QA-002 | Test Run | P0 | 支持 Pass/Fail/Blocked 和证据 |
| QA-003 | Bug | P0 | 支持严重级别、复现步骤及 Task/MR/Test 关联 |
| QA-004 | AI 生成测试用例 | P0 | 基于 Requirement + UX + API/MR 生成草稿 |
| QA-005 | Bug 回流 | P0 | Bug 可回流 Developer，修复后重新回归 |
| QA-006 | 质量汇总 | P0 | 展示用例统计、阻断缺陷与发布建议输入 |

### 9.9 Release

| ID | 需求 | 优先级 | 验收摘要 |
|---|---|:---:|---|
| REL-001 | Release Candidate | P1 | 记录版本、关联 Work Items、CI、QA 和 Bug 状态 |
| REL-002 | 发布前检查 | P1 | 检查 CI、QA、未关闭高优 Bug 和审批策略 |
| REL-003 | Release Note | P1 | Agent 可根据交付资产生成草稿 |
| REL-004 | 人工审批 | P1 | 生产部署、回滚等 HIGH 操作必须审批 |
| REL-005 | Deployment 记录 | P1 | MVP 可模拟部署，但必须记录环境、状态和结果 |
| REL-006 | 真实生产部署 | P2 | 后续通过受控 Adapter 接入 |

---

## 10. Agent 产品需求

### 10.1 定位

Agent 是 ForgeAI 的智能执行层，不是独立聊天产品。用户提出目标后，Agent 根据当前用户、Workspace、Project、Work Item、角色和授权范围构建上下文，制定计划、调用 Tool、展示过程，并输出结果或等待审批。

### 10.2 能力层次

| 能力 | 说明 | MVP |
|---|---|:---:|
| Understand | 理解意图、项目与工作项上下文 | 必须 |
| Plan | 形成可执行计划并展示关键步骤 | 必须 |
| Retrieve | 从文档、Work Item、GitLab、CI 等获取上下文 | 必须 |
| Execute | 调用 Java 业务 Tool 完成真实操作 | 必须 |
| Verify | 检查操作结果和前置条件 | 必须 |
| Delegate | 委派给角色 Skill/子 Agent | P1 |
| Browser Automation | API 不可用时操作第三方系统 | P2 |

### 10.3 使用入口

- 全局 Command Center：任意页面发起指令。
- Work Item Context Agent：自动附带当前项目和工作项。
- UX/Development/QA/Release 页面角色入口。
- Agent Run 页面：计划、步骤、Tool、引用、审批、错误和结果。

### 10.4 Tool 范围

| 分类 | Tool 示例 | MVP |
|---|---|:---:|
| Project | `get_project`, `get_members` | P0 |
| Requirement | `create_requirement`, `update_requirement`, `get_requirement` | P0 |
| UX | `create_ux_task`, `create_ux_document`, `submit_ux_review` | P0 |
| Task | `create_dev_task`, `create_qa_task`, `assign_task` | P0 |
| Document | `create_document`, `update_document`, `search_documents` | P0 |
| GitLab | `get_repo`, `create_branch`, `get_mr`, `create_merge_request` | P0 |
| CI | `trigger_pipeline`, `get_pipeline`, `get_pipeline_logs` | P0 |
| QA | `create_test_case`, `create_test_run`, `create_bug` | P0 |
| Release | `release_precheck`, `create_release`, `deploy_release` | P1 |
| Browser | `navigate`, `click`, `fill`, `extract`, `screenshot` | P2 |

### 10.5 权限与审批

| 风险级别 | 示例 | 策略 |
|---|---|---|
| LOW | 查询、检索、生成草稿 | 有权限即可执行 |
| MEDIUM | 创建 Task/文档/Branch/MR、触发 CI、修改状态 | Workspace 可配置确认策略 |
| HIGH | 合并受保护分支、生产发布、回滚、删除 | 必须 Human Approval |

Agent 的有效权限为：用户权限 ∩ Skill Tool Allowlist ∩ Project Policy ∩ Resource Scope ∩ Risk Policy。Agent 不直接访问 MySQL、GitLab Token 或部署凭据。

### 10.6 Agent Runtime

首选 Python 3.12 + FastAPI + Deep Agents（基于 LangGraph）。使用本项目的 Agent Gateway/Runtime Adapter 隔离框架变化；Tool Contract 与 Java 业务 API 不依赖某个具体 Agent Runtime。

---

## 11. 模型、数据与外部服务

### 11.1 模型配置

- 部署方提供 LLM/Embedding API Key，ForgeAI 不默认代管。
- 配置包含 Provider、Endpoint、Model、API Key 和用途。
- Agent 通过统一 Model Adapter 调用，不在业务代码硬编码厂商。
- 后续支持 Agent 主模型、Embedding、Rerank/辅助模型和 OpenAI-compatible 本地端点。

### 11.2 最新技术基线

| 组件 | 选型 | 用途 |
|---|---|---|
| `forge-web` | Next.js + React + TypeScript + Arco Design | Web 工作台 |
| `forge-server` | Java 21 + Spring Boot 3.x | 业务权威、API、权限、工作流、集成 |
| `forge-agent` | Python 3.12 + FastAPI + Deep Agents（基于 LangGraph） | Agent Runtime、RAG、Tool Calling |
| 数据库 | MySQL 8 | 业务事实、配置、Trace、审计 |
| 会话/缓存 | Redis | Spring Session、短期状态、限流 |
| 向量检索 | Qdrant | 项目文档向量索引 |
| Git/CI | GitLab + GitLab CI | Repository、Branch、MR、Pipeline |
| 流式事件 | SSE | Agent 执行过程 |
| 部署 | Nginx + Docker Compose | 自托管单机起步 |

---

## 12. 开源与自托管要求

- 源码完整开源，官方 GitHub 为 `forge-ai/forge`。
- 根目录为 `forge-ai`，核心模块为 `forge-web`、`forge-server`、`forge-agent`。
- 提供 README、Quick Start、Docker Compose、配置、备份、恢复和升级说明。
- 个人、团队或公司可独立部署，不依赖 ForgeAI 中心化账号服务。
- 实例之间不共享数据库、Agent Memory、模型 Key 或 GitLab Token。
- 配置与 Secret 通过环境变量或 Secret 文件注入，禁止提交 Git。
- 默认不收集业务正文遥测；可选遥测必须透明且可关闭。

### 12.1 MVP 部署拓扑

| 组件 | 部署方式 | 说明 |
|---|---|---|
| Nginx | 本地可选 / 腾讯云必需 | 本地可简化；上线提供 HTTPS、反向代理、SSE |
| forge-web | Docker Desktop / 腾讯云 Docker | Next.js |
| forge-server | Docker Desktop / 腾讯云 Docker | Spring Boot |
| forge-agent | Docker Desktop / 腾讯云 Docker | FastAPI + Agent Runtime |
| MySQL | 本机 Docker Desktop / 腾讯云 Docker | 业务事实，必须持久化和备份 |
| Redis | 本机 Docker Desktop / 腾讯云 Docker | Session 与短期状态 |
| Qdrant | 本机 Docker Desktop / 腾讯云 Docker | 向量索引，可重建 |
| Worker（可选） | 本机 Docker Desktop / 腾讯云 Docker | 文档索引、Webhook、异步任务 |

---

## 13. 安全与权限要求

- 所有业务 API 在 `forge-server` 执行认证、授权和资源归属检查。
- 使用 Cookie + Spring Session Redis + BCrypt；生产 Cookie 启用 HttpOnly/Secure/SameSite 和 CSRF 防护。
- Tool 调用复用同一授权能力，不能因来自 Agent 而绕过权限。
- Agent 只接收短时 Run Context；调用 Java 时由 Backend 重新读取当前权限。
- GitLab Token、LLM API Key 等 Secret 加密保存，不得进入业务日志、Prompt 或 Tool 输出。
- 上传文档视为不可信数据，不能通过 Prompt Injection 改变系统权限或 Tool 白名单。
- HIGH 操作记录请求人、审批人、Agent Run、Tool Call、冻结参数和执行结果。
- 关键操作写入不可通过普通业务 API 修改的 Audit Log。
- 所有跨 Workspace 数据访问与 Qdrant 检索必须在服务端过滤。

---

## 14. Agent 可观测性与评测

### 14.1 Agent Trace

- 每次请求生成 Agent Run ID。
- 记录 Step、输入/输出摘要、状态、耗时和错误。
- Tool Call 记录 Tool、脱敏参数、权限、风险、审批、时间与结果。
- 在 Provider 可用时记录模型、Token、延迟和估算成本。
- SSE 可实时显示，断线后按持久化事件恢复。

### 14.2 MVP Evaluation

- 建立 20–50 个固定场景，覆盖 Product、UX、Developer、QA、Release 和越权拒绝。
- 指标包括 Task Success Rate、Tool Selection Accuracy、Approval Correctness、Average Latency、Token/Cost。
- 确定性 Tool/权限契约测试进入 CI；模型质量评测单独报告。

---

## 15. 非功能需求

| 类别 | 要求 | MVP 指标/约束 |
|---|---|---|
| 可用性 | 单人可部署、启动简单 | Docker Compose；README 可独立完成部署 |
| 性能 | 普通 CRUD 及时响应 | API P95 < 500ms，不含外部 AI/GitLab |
| Agent 体验 | 执行过程可见 | SSE 展示计划、步骤、Tool 和审批 |
| 可靠性 | 失败可恢复 | Run/Step/Tool 持久化；写操作幂等 |
| 安全 | 最小权限 | RBAC + Tool Authorization + Approval |
| 可维护性 | 单人维护友好 | 模块化单体，不做业务微服务 |
| 可扩展性 | Runtime 可替换 | Agent Gateway + Tool Contract 隔离 |
| 隐私 | 最小上下文 | 按 Workspace/Project/Work Item 过滤 |

---

## 16. 产品级架构原则

1. `forge-server` 是业务系统 Source of Truth。
2. `forge-agent` 是智能执行层，只通过 Tool 调用 Java 业务能力。
3. `forge-web` 是统一交互入口，不直连 Agent/数据库/向量库/GitLab。
4. 开发者在 IDE/CLI 编码；ForgeAI 管理研发上下文和交付流程。
5. GitLab 是 Repository、Branch、MR、Pipeline 的权威系统。
6. Workflow 先固定，再配置化。
7. Agent 默认只拥有当前用户和 Skill 共同允许的 Tool。
8. 高风险操作 Human-in-the-loop。
9. Browser Automation 遵循 API First，只作为后续能力。
10. Agent 操作可追踪、可审计、可恢复。

---

## 17. MVP 页面范围

| 页面 | 核心内容 | 优先级 |
|---|---|:---:|
| 登录/初始化 | Admin 初始化、登录 | P0 |
| Workspace 首页 | 项目、待办、近期活动 | P0 |
| Project Overview | Product/UX/Dev/QA/Release 概览 | P0 |
| Requirement Detail | PRD、状态、关联交付资产、Agent | P0 |
| UX Workspace | UX Task、用户流程、UX Spec、原型与 Review | P0 |
| Task Board | 按类型/角色/状态的任务看板 | P0 |
| Document Editor | PRD、UX、技术、API、测试、发布文档 | P0 |
| Development | Repository、Branch、MR、Pipeline | P0 |
| QA | Test Case、Test Run、Bug | P0 |
| Release | Candidate、Precheck、Approval、Deployment | P1 |
| Agent Command Center | 指令、计划、Tool、审批和结果 | P0 |
| Agent Trace | Run、Step、Tool Call | P1 |
| Settings | 模型、GitLab、成员、角色、项目策略 | P0 |

---

## 18. MVP 完成定义

MVP 以一条真实交付闭环能否运行定义完成：

1. ForgeAI 可在安装 Docker Desktop 的本机通过浏览器访问，并可按文档部署到腾讯云 CVM。
2. Admin 可初始化实例、配置模型、连接 GitLab、创建 Workspace/Project。
3. Product 可创建 Requirement 和 PRD。
4. UX 可从 PRD 创建 UX Task，完成 UX Spec、原型关联和评审。
5. Developer 可读取 PRD + UX，管理 Dev Task、Branch、MR 和 CI；编码仍在本地完成。
6. QA 可基于需求、UX、API/MR 生成用例，执行测试并创建 Bug。
7. Release Precheck 可聚合 CI、QA 与未关闭 Bug，并请求审批。
8. Work Item 可追踪 PRD、UX、Task、MR、Pipeline、Test、Bug 和 Release。
9. Agent 至少完成一组覆盖五个角色的真实 Tool 调用，并展示执行过程。
10. HIGH Tool 必须出现人工审批；越权操作被服务端拒绝。
11. 所有 Agent Run 有 Trace，关键 Tool Call 有 Audit Log。
12. 仓库提供完整 README、Compose、配置说明与黄金 Demo 数据。

---

## 19. 版本路线图

| 阶段 | 范围 | 目标 |
|---|---|---|
| MVP-0 基础 | 三模块、登录、Workspace、MySQL、Redis、Qdrant、Docker | 系统骨架可运行 |
| MVP-1 Product/UX | Requirement、PRD、UX Task/文档/Review、Workflow | Product → UX → Ready for Dev |
| MVP-2 Development | Dev Task、GitLab、Branch、MR、CI | 开发交付上下文打通 |
| MVP-3 QA | Test Case、Test Run、Bug、回流 | 测试闭环打通 |
| MVP-4 Agent | Tool、RAG、SSE、Approval、Trace | AI 可受控执行 |
| MVP-5 Release | Candidate、Precheck、Deployment Record | 完成交付闭环 |
| Post-MVP | Code Sandbox、Browser Agent、SSO、可配置 Workflow、K8s | 扩展企业与自治能力 |

---

## 20. MVP 成功指标

| 指标 | 目标 |
|---|---|
| 端到端需求闭环 | 至少成功跑通 1 个 Requirement 到 Release Candidate/Deployment Record |
| UX 阶段完整性 | 黄金 Demo 100% 包含 UX Task、UX 文档和 Review |
| Agent Tool 成功率 | 固定 Demo 场景 > 90% |
| Agent 任务成功率 | 关键 MVP 用例 > 80% |
| 部署成功率 | 新环境按 README 可完成部署 |
| 交付上下文覆盖 | Requirement 可关联 PRD、UX、Task、MR、CI、Test、Bug、Release |
| 可追踪性 | 100% Agent Run 有基础 Trace，关键 Tool Call 有审计 |
| 越权保护 | 跨 Workspace 和未经审批的 HIGH Tool 测试 100% 拒绝 |

---

## 21. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 范围过大 | 极高 | 严格执行非目标，只围绕 Product→UX→Dev→QA→Release |
| Agent 不稳定 | 高 | Tool 小而明确；业务规则由 Java 控制；关键操作审批 |
| Runtime 版本变化 | 中 | Agent Gateway 隔离；锁依赖；契约和 E2E 测试 |
| UX 能力膨胀为 Figma | 高 | 只做交付文档、附件、外链与 Review |
| 私有 GitLab 差异 | 中 | 使用稳定 API、Base URL、Adapter 和沙箱测试 |
| 部署复杂 | 中 | Compose、一套 `.env.example`、备份和升级说明 |
| 模型成本/合规 | 中 | 自带 Key、Token 统计、最小上下文、Provider 抽象 |
| Browser 自动化脆弱 | 低（MVP） | 延后到 P2，坚持 API First |

---

## 22. 已确定的关键技术决策

旧版 PRD 中留待技术阶段决定的事项，现已更新为：

1. RAG 使用 **MySQL + Qdrant**；MySQL 是事实来源，Qdrant 是可重建派生索引。
2. Java 与 Agent 使用短时 Run Context/服务间认证；Tool 执行时 Java 重新鉴权。
3. MVP Tool 由 Python 调用 Java REST API；MCP 可作为未来适配协议，不是当前内部强依赖。
4. Agent 实时事件使用 SSE；Run/Step/Tool/Event 持久化。
5. GitLab Token 加密保存，前端和 Agent 不接触明文。
6. Work Item 使用统一表 + `type`。
7. Workflow 先采用自定义固定状态机，不引入 Spring State Machine。
8. 文档使用版本模型，发布版本异步分块并索引到 Qdrant。
9. Agent Runtime 使用 Deep Agents（基于 LangGraph），但通过 ForgeAI Adapter 隔离。
10. 本地开发时 MySQL/Redis/Qdrant 必须运行于本机 Docker Desktop Compose 网络；最终上线时运行于腾讯云 CVM 的私有 Docker 网络，生产可按策略替换外部服务。

---

## 23. 黄金 Demo 验收脚本

1. Admin 登录，创建 “Demo Shop” Project，配置 GitLab Repository。
2. Product 创建 Requirement：“增加手机号验证码登录”。
3. Product 调用 Agent 生成 PRD、目标、范围和验收标准。
4. UX Agent 创建 UX Task，生成用户流程、页面清单、正常/异常状态和交互要求。
5. UX 关联原型并提交 Review；Product/UX 确认后进入 Ready for Development。
6. Developer Agent 基于 PRD + UX 生成技术方案和前后端 Dev Tasks，并创建 Branch。
7. Developer 在本地 IDE 完成代码；ForgeAI 关联 MR 并触发 CI。
8. QA Agent 根据 PRD + UX + API/MR 生成 Test Cases。
9. QA 执行测试并创建一个关联 Requirement/Task/MR/Test 的 Bug。
10. Developer 修复后再次提交，CI 和回归通过。
11. Release Agent 执行 Precheck，生成 Release Candidate 与 Release Note。
12. 点击发布触发 Human Approval；批准后记录 Deployment（MVP 可模拟）。
13. Delivery Graph 展示完整交付链；Agent Trace 展示 Run、Step、Tool 与审批记录。

---

## 24. 仓库与模块命名

```text
forge-ai/
├── forge-web/       # Next.js + TypeScript + Arco Design
├── forge-server/    # Java 21 + Spring Boot
├── forge-agent/     # Python 3.12 + FastAPI + Deep Agents（基于 LangGraph）
├── packages/
├── deploy/
├── docs/
└── README.md
```

- GitHub：`forge-ai/forge`
- Java 根包建议：`ai.forge.server`
- Python 包建议：`forge_agent`
- 前端 npm package 建议：`@forge-ai/forge-web`
- Docker 服务/镜像名使用 `forge-web`、`forge-server`、`forge-agent`

---

## 25. 参考资料

1. LangChain Deep Agents（Agent Runtime）：<https://github.com/langchain-ai/deepagents>
2. Deep Agents documentation：<https://docs.langchain.com/oss/python/deepagents/overview>
3. LangGraph（Deep Agents 底层图框架）：<https://github.com/langchain-ai/langgraph>

> Agent 框架、库版本和外部服务能力可能变化；工程实施时应锁定兼容版本，并用 ForgeAI 自身的 Gateway、Tool Contract 和测试隔离框架升级。

---

本 PRD 与《ForgeAI 完整技术方案 v1.3》共同构成 ForgeAI MVP 的产品和技术基线。任何影响 Product → UX → Development → QA → Release 主流程、品牌命名、MVP 边界或安全策略的变更，都需要同步更新 PRD、技术方案、README、API/Tool Contract 与验收测试。
