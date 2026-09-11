# ForgeAI 上下文路由索引

> 本文档用于将当前任务定位到最小必读上下文。公司级架构基线 v2 是当前产品与工程事实；旧 v1 文档仅用于追溯历史。

## 1. 读取协议

1. 先遵守根目录 `AGENTS.md`。
2. 用户给出会话编号时，读取本索引中对应的“会话路由”；未给出时，使用“主题路由”。
3. 在目标文档中用章节标题定位，仅读取命中标题下的内容；不使用固定行号，避免文档编辑后路由失效。
4. 同一任务有多个目标时，先写出“目标 → 会话/主题 → 章节”映射，再对章节去重。
5. 默认不通读任何一份基线文档。只有索引未覆盖、命中章节相互冲突、用户明确要求全局评审，或变更需要 ADR 时，才扩大读取范围。
6. 读取设计文档后仍必须检查目标目录下的现有代码、测试和更深层 `AGENTS.md`；设计文档不替代代码现状。

## 2. 文档代号与事实优先级

| 代号 | 文档 | 使用场景 |
|---|---|---|
| `COMPANY` | `docs/design/ForgeAI_公司级架构基线_v2.md` | 当前产品作用域、数据、权限、API、路由和部署基线 |
| `PRD` | `docs/design/ForgeAI_PRD_v0.2.md` | 产品目标、用户旅程、范围、非目标和产品验收 |
| `ARCH` | `docs/design/ForgeAI_完整技术方案_v1.3.md` | 总体架构、技术选型、跨模块边界、部署和风险 |
| `DETAIL` | `docs/design/ForgeAI_详细设计_v1.0.md` | 可编码的数据、状态机、权限、API、Agent、SSE、GitLab、测试与部署契约 |
| `GUIDE` | `docs/development/ForgeAI_分阶段开发指南_v1.0.md` | 会话范围、学习目标、验证和本轮明确不做的内容 |

新实施先读取 `COMPANY`；`PRD`、`ARCH`、`DETAIL`、`GUIDE` 均为历史基线，只在追溯旧功能时使用，且任何冲突均以 ADR-016 和 `COMPANY` 为准。

## 3. 会话路由

每轮首先读取 `GUIDE` 中完全匹配的“会话 NN”小节，然后只读取下表列出的设计章节。

| 会话 | 主题 | 必读设计章节 |
|---:|---|---|
| 01 | 仓库基线、ADR、质量门禁 | `ARCH` 3、5、27；`DETAIL` 3.1、18 |
| 02 | Server 骨架与模块边界 | `ARCH` 5.1、10.1；`DETAIL` 2.4、3、7、8.1、18.1 |
| 03 | MySQL、Redis、Qdrant、Flyway | `ARCH` 6、18、21.1；`DETAIL` 3.4、4.1、7.2–7.3、17.1–17.2 |
| 04 | Web 骨架、设计系统、Client | `ARCH` 5、16；`DETAIL` 8.1、13 |
| 05 | Agent 骨架、契约、Smoke | `ARCH` 4、12–13；`DETAIL` 2.1–2.3、8.4、9.5–9.6 |
| 06 | 实例初始化与密码 | `DETAIL` 4.2、6、14.1 |
| 07 | 登录、Session、退出 | `ARCH` 9；`DETAIL` 4.2、14.1 |
| 08 | CSRF 与 Web 登录闭环 | `DETAIL` 8、13、14.1 |
| 09 | 公司成员范围与审核 | `COMPANY` 2–5 |
| 10 | RBAC 与权限矩阵 | `ARCH` 8；`DETAIL` 4.3、6、14.2 |
| 11 | Work Item 聚合与原子编号 | `DETAIL` 3.2–3.4、4.4、7、8 |
| 12 | Requirement 状态机与 Guard | `DETAIL` 4.4、5.1–5.2、5.4、6 |
| 13 | 文档不可变版本与编辑器 | `DETAIL` 4.5、8.3、13.4 |
| 14 | Product 纵向切片 | `PRD` 7.1、9.3–9.4；`DETAIL` 5.2、8、13.2–13.3 |
| 15 | UX Task、UX 文档与评审 | `PRD` 7、9.5；`DETAIL` 4.4–4.5、5.3、13.2 |
| 16 | UX 跳过、关系与 Activity | `PRD` 7.2；`DETAIL` 4.4、5.2–5.3、6 |
| 17 | Delivery Graph 与 Product/UX E2E | `PRD` 6.3、7；`DETAIL` 4.4–4.5、16.3 |
| 18 | Agent Run、Trace 与 SSE | `ARCH` 11–12；`DETAIL` 4.8、9.1、10 |
| 19 | Agent Gateway、Deep Agents、Checkpoint | `DETAIL` 2.1–2.3、8.4、9.1–9.4 |
| 20 | Outbox、索引、权限过滤 RAG | `ARCH` 14、18.1；`DETAIL` 3.4、4.5、9.8、14.2 |
| 21 | Tool Registry 与 Product/UX Tool | `ARCH` 13；`DETAIL` 8.4、9.5–9.6 |
| 22 | 审批、暂停/恢复与 Agent E2E | `ARCH` 13.3–13.4；`DETAIL` 4.8、9.7、16.4 |
| 23 | SecretService 与 GitLab 连接 | `ARCH` 15、17.1；`DETAIL` 3.5、4.6、11.1–11.2、14 |
| 24 | Dev Task、Branch 与 MR | `DETAIL` 4.4、4.6、5.3、11.1、11.3 |
| 25 | Pipeline、Webhook 与 Outbox | `DETAIL` 3.4、4.6、10、11.4 |
| 26 | Development 到 QA Guard | `DETAIL` 5.2–5.4、11、13.2 |
| 27 | Developer Skill 与 GitLab 恢复 | `DETAIL` 9.5–9.6、11、15.4、16.4 |
| 28 | Test Case、Test Run 与 QA Guard | `PRD` 9.8；`DETAIL` 4.7、5.3、12.1、16 |
| 29 | Bug 状态机、开发回流、QA Agent | `PRD` 9.8；`DETAIL` 4.4、5.3、9.5–9.6、12.1 |
| 30 | Release、Precheck 与 Release Note | `PRD` 9.9；`DETAIL` 4.7、12.2、16 |
| 31 | HIGH 审批、模拟部署、后半程 E2E | `ARCH` 13.3–13.4；`DETAIL` 4.8、9.7、12.3、16.3 |
| 32 | 黄金 Demo 与端到端回归 | `PRD` 18、23；`ARCH` 25；`DETAIL` 16.3–16.4 |
| 33 | 安全、可靠性、性能与故障演练 | `ARCH` 17–20；`DETAIL` 14–16、20.2–20.3 |
| 34 | 本机 Compose、腾讯云与面试叙事 | `ARCH` 21–23；`DETAIL` 17–18、20 |

## 4. 主题路由

仅在用户未给出会话编号时使用。若同时命中多行，只合并与当前目标直接相关的章节。

| 任务主题或关键词 | 默认路由 |
|---|---|
| 产品定位、MVP 范围、用户旅程、成功指标 | `PRD` 1–5、7、18–20 |
| 命名、仓库、模块边界、契约 | `ARCH` 3–5；`DETAIL` 2–3 |
| 登录、Session、CSRF、初始化 | `ARCH` 9；`DETAIL` 4.2、6、14.1 |
| 公司、成员、RBAC、账号审核 | `COMPANY` 2–5；ADR-016 |
| Work Item、Requirement、Task、Bug、关系 | `DETAIL` 4.4、5 |
| 文档、版本、附件、编辑器 | `DETAIL` 4.5、8.3、13.4、14.3 |
| API、DTO、错误、分页、幂等 | `ARCH` 10；`DETAIL` 3.2–3.3、8 |
| MySQL、Flyway、Mapper、事务、锁、Outbox | `DETAIL` 3.3–3.4、4.1、7 |
| Agent Run、Deep Agents、Checkpoint、Skill | `ARCH` 12；`DETAIL` 4.8、9.1–9.5 |
| Tool、风险、审批、暂停与恢复 | `ARCH` 13；`DETAIL` 8.4、9.6–9.7 |
| RAG、Embedding、Qdrant、索引 | `ARCH` 14；`DETAIL` 4.5、9.8 |
| SSE、事件重放、Reducer | `ARCH` 11；`DETAIL` 10 |
| GitLab、Secret、Branch、MR、Pipeline、Webhook | `ARCH` 15；`DETAIL` 3.5、4.6、11 |
| QA、Test Run、Bug 回流 | `DETAIL` 4.7、5.3、12.1、16 |
| Release、Precheck、Deployment | `DETAIL` 4.7、12.2–12.3、16 |
| forge-web、页面、组件、Hook、状态管理 | `ARCH` 16；`DETAIL` 13 |
| 安全、日志、秘密、租户攻击面 | `ARCH` 17；`DETAIL` 3.5、14 |
| 可观测性、可靠性、性能、测试 | `ARCH` 18–20；`DETAIL` 15–16 |
| Docker Compose、腾讯云、备份、恢复、升级 | `ARCH` 21–23；`DETAIL` 17 |

## 5. 扩大读取的条件

- 产品范围或验收存在歧义：增读 `PRD` 中对应功能需求、产品原则或完成定义。
- 跨应用调用方向、事实来源或部署拓扑存在歧义：增读 `ARCH` 的相关架构章节。
- 实施契约不足：在 `DETAIL` 中增读直接上下游小节，而不是整份文档。
- 准备跨越当前会话边界：停止实施，先报告与 `GUIDE` 的范围冲突。
- 任务会改变事实来源、认证、事务、Agent Tool 安全边界或部署拓扑：先检索 `docs/adr`，必要时新增 ADR。
