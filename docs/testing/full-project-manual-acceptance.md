# ForgeAI 全项目人工验收测试用例集（公司级模型版）

本手册以 ADR-016 和公司级架构基线 v2 为准。一个部署实例只服务一家公司，`organizations` 是唯一业务作用域；公司下直接创建需求、任务、文档、Agent Run、测试和发布，不存在 ForgeAI Workspace、Project、默认 scope 或兼容 scope。

GitLab Project ID、Docker Compose project 和 Python `pyproject.toml` 属于第三方或工程术语，不代表 ForgeAI 业务 Project。每条用例必须记录截图、Trace、Request ID、终端日志或缺陷编号；任一 P0 失败时停止发布判断，修复后重跑所属模块及第十三节全部门禁。

## 一、执行信息

| 字段                       | 填写值                         |
| -------------------------- | ------------------------------ |
| 执行人                     |                                |
| 开始/结束时间              |                                |
| Git commit                 |                                |
| 浏览器及版本               |                                |
| Docker Desktop 版本        |                                |
| 操作系统/CPU/内存          |                                |
| `deploy/.env` 是否仅供本机 | 是 / 否                        |
| 最终结论                   | PASS / CONDITIONAL PASS / FAIL |

优先级：P0 表示安全、授权、事实一致性或主链阻断；P1 表示核心功能异常；P2 表示非阻断体验问题。执行后勾选标题并填写“实际/证据”，不得只写“正常”。

## 二、环境与测试数据

### 2.1 前置条件

- Docker Desktop 可用，根目录存在仅供本机使用且未提交的 `deploy/.env`。
- Node、npm、Java、浏览器及项目依赖已按 README 准备。
- 测试端口未被占用，故障演练期间无人共享 Compose 环境。
- 准备一个不超过 2 MiB 的有效 WebP 公司 Logo。
- GitLab 用例使用可控测试实例或 stub，不连接生产仓库。

```bash
git rev-parse HEAD
docker compose version
make test-apps-up
make test-apps-ready
```

通过标准：MySQL、Redis、Qdrant、`forge-agent`、`forge-server`、`forge-web` 均 running/healthy；`http://127.0.0.1:13000` 有响应。

实际/证据：

### 2.2 空环境与建议数据

主回归从空数据库开始，通过公开页面/API 创建全部业务事实，不装载预制业务链。

| 数据                          | 建议值                          |
| ----------------------------- | ------------------------------- |
| 公司                          | `ForgeAI UAT`，Slug `forge-uat` |
| Owner                         | `owner@uat.forgeai.local`       |
| Product / UX / Developer / QA | 对应前缀的 `@uat.forgeai.local` |
| Release Approver              | `approver@uat.forgeai.local`    |
| 主需求 / 负向需求             | `UAT 手机登录` / `UAT 支付回调` |

```bash
docker compose --profile applications --env-file deploy/.env -f deploy/compose.yml -f deploy/compose-test.yml down
docker volume rm forge-ai-test_mysql-data forge-ai-test_qdrant-data
make test-apps-up
make test-apps-ready
```

`docker volume rm` 会永久删除指定测试卷。Agent checkpoint 卷默认保留；仅在明确要求清除运行历史时另行确认。这里的 Compose project 是 Docker 术语。

## 三、拓扑、初始化与认证

### ENV-01 [P0] 完整拓扑与最小暴露面 [✅]

步骤：执行 `make test-apps-ready` 并检查端口。

预期：六个服务健康；Web 与测试数据服务仅绑定 `127.0.0.1`；Server 与 Agent 不直接暴露宿主机端口。

实际/证据：

### AUTH-01 [P0] 空实例初始化 [✅]

步骤：打开首页；上传合法 WebP Logo，填写 Owner、公司名称与 Slug；提交、刷新并再次初始化。

预期：只原子创建公司、Owner、公司成员关系和 Owner 角色；Logo 可读取；重复初始化冲突且无第二家公司或 Owner；页面、API、数据库均不创建 ForgeAI Workspace/Project。

实际/证据：

### AUTH-02 [P0] 初始化字段与 Logo 校验 [✅]

步骤：在独立空环境提交空字段、非法 Slug、弱密码、非 WebP、伪造媒体类型、损坏内容和超限 Logo。

预期：全部失败且无部分事实；错误不回显密码或 Base64；合法 Logo 响应为 `image/webp`。

实际/证据：

### AUTH-03 [P0] 登录、Session 与当前用户 [✅]

步骤：Owner 登录，检查跳转、Cookie 和 `/api/v1/me`。

预期：进入 `/overview`；`FORGE_SESSION` 为 HttpOnly、Path `/`、SameSite=Lax；只返回实时有效的公司成员与角色，不返回密码、哈希或 Session 明文。

实际/证据：

### AUTH-04 [P0] 错误登录不枚举账户 [✅]

步骤：使用不存在邮箱、错误密码、待审核账号和已停用账号登录。

预期：均无有效 Session；文案不泄露可枚举账户事实；适用时带 Request ID。

实际/证据：

### AUTH-05 [P0] 未登录访问与退出 [✅]

步骤：无痕访问全部业务页面/API；登录后退出，再后退、刷新并重放旧 Cookie。

预期：未登录看不到业务标题或内容；退出后旧 Session 失效，缓存不能恢复数据。

实际/证据：

### AUTH-06 [P0] CSRF、Origin 与请求 ID [✅]

步骤：执行 `make hardening-test`；人工发送缺失/错误 CSRF、非法 Origin、超长或带换行的 `X-Request-ID`。

预期：修改请求 fail-closed，GET 不被误伤；非法 ID 被替换且不能注入日志。

实际/证据：

## 四、公司成员、角色与作用域

### ORG-01 [P0] 公司级信息架构 [✅]

步骤：遍历需求概览、我的需求、任务看板、Agent 指令、执行轨迹、成员管理、集成设置；检查 URL、表单和 Network。

预期：无 Workspace/Project 创建、选择或切换入口；无旧 `/w/.../p/...` 路由；公开业务请求不要求作用域参数。

实际/证据：

### ORG-02 [P0] 注册与待审核 [✅]

步骤：分别申请 PRODUCT、UX、DEVELOPER、QA、RELEASE_APPROVER，注册后立即登录。

预期：合法申请返回 `PENDING`，审核前不能登录；Owner 在成员管理看到申请。

实际/证据：

### ORG-03 [P0] 注册输入与提权边界 [✅]

步骤：测试重复邮箱、弱密码、非法邮箱、空/未知角色，并尝试 OWNER、ADMIN。

预期：重复邮箱冲突；仅公开业务角色可申请；不能自助提权；失败无部分用户、成员或角色事实。

实际/证据：

### ORG-04 [P0] 审核、角色修改与启停 [✅]

步骤：Owner 审核申请、修改角色、停用并启用成员；普通成员尝试相同操作。

预期：Owner 操作成功且有审计；激活后方可登录；停用后现有 Session 不再取得权限；普通成员被拒绝。

实际/证据：

### ORG-05 [P0] 成员并发更新 [✅]

步骤：两个窗口读取同一成员，A 更新后 B 用旧 `expectedVersion` 更新。

预期：仅 A 成功；B 冲突；无角色或状态丢失更新。

实际/证据：

### ORG-06 [P0] 公司作用域不可注入 [✅]

步骤：向公开 API 追加当前/伪造 `organizationId`，读取不存在或其他公司测试夹具资源；执行 `make hardening-test`。

预期：仅从 Session 解析公司；客户端不能扩大范围；跨公司/不存在资源不泄露标题、正文、成员或 Trace；Tool 与 RAG 同样受限。

实际/证据：

### ORG-07 [P0] 旧模型物理缺席 [✅]

步骤：检查 OpenAPI、浏览器请求和 V1 数据库基线；请求旧资源路由。

预期：无旧端点或作用域参数；业务表使用 `organization_id`；不存在 `workspaces`、`projects`、`project_members` 和 `workspace_id/project_id` 业务列；GitLab `remote_project_id` 不计入失败。

实际/证据：

## 五、工作台、搜索、通知与需求

### HOME-01 [P1] 公司仪表盘 [ ]

步骤：检查空环境；创建和推进数据后刷新。

预期：个人待办、本周交付、活跃 Agent、阶段分布、趋势与公司事实一致且不跨公司。

实际/证据：

### HOME-02 [P1] 全局搜索 [ ]

步骤：用顶部搜索或 `⌘/Ctrl+K` 搜索需求、编号、文档、成员；测试短词、无结果和无权关键词。

预期：合法结果可导航；少于两个字符不查询；无权内容不出现在结果或片段中。

实际/证据：

### HOME-03 [P1] 通知与未读数 [ ]

步骤：制造通知，核对列表、未读数、标记已读和刷新。

预期：未读数准确；只能读取本人可见通知；标记已读幂等。

实际/证据：

### REQ-01 [P0] 创建权限与公司级编号 [✅]

步骤：Owner、PRODUCT 创建需求；其他角色直接请求创建；提交非法字段。

预期：仅获授权角色成功；所有 Work Item 共享公司级单调 `REQ-n` 序列，唯一不复用但允许间隔；失败无部分事实。

实际/证据：

### REQ-02 [P1] 搜索、筛选与分页 [ ]

步骤：创建多状态需求，组合搜索、状态筛选和分页。

预期：统计、过滤、稳定排序正确；列表仅返回摘要；切页无重复或遗漏。

实际/证据：

### REQ-03 [P0] 参与人与“我的需求” [ ]

步骤：关联 PRODUCT、UX、DEVELOPER、QA；测试角色错配、同角色多人、失效成员；各账号打开 `/my-requirements`。

预期：每角色最多一名有效匹配成员；非法组合整体失败；页面只返回参与需求，Owner 可按公司权限代行。

实际/证据：

### BOARD-01 [P1] 公司任务看板 [ ]

步骤：打开 `/task-board`；拖动任务跨泳道并调整顺序，刷新；提交非法泳道和旧版本。

预期：只显示当前公司事实；合法位置持久化；非法泳道、无权任务和并发更新被拒绝。

实际/证据：

## 六、Product、文档与 UX

### PROD-01 [P1] Requirement 材料 [ ]

步骤：填写目标、范围、排除范围、至少两条验收标准和业务价值，保存并刷新。

预期：内容持久化，version 递增，Activity 留痕。

实际/证据：

### PROD-02 [P0] Product Guard 与评审 [ ]

步骤：缺材料/已发布 PRD 时提交或批准；补齐后走拒绝、修改、重提和批准。

预期：缺项时状态、version、副作用不变；拒绝原因必填；补齐后按服务端 `availableActions` 推进。

实际/证据：

### DOC-01 [P1] 不可变版本与发布 [ ]

步骤：创建 PRD，连续保存、查看版本并发布；对 UX Spec、技术设计复核。

预期：每次保存生成新版本；旧版本可读；发布指向指定版本；内容和关系刷新后保持。

实际/证据：

### DOC-02 [P0] 文档乐观锁 [ ]

步骤：两个窗口打开文档，A 保存/发布后 B 用旧 version 操作。

预期：B 被拒绝；A 不被覆盖；current version 唯一正确。

实际/证据：

### UX-01 [P1] UX Task、Spec 与评审 [ ]

步骤：开始 UX Task；缺发布/checklist 时提交；补齐后拒绝、修改、重提和批准。

预期：Guard 明确缺项；原因和旧版本保留；批准后到正确开发就绪态，Activity 与关系图同步。

实际/证据：

### UX-02 [P0] Skip UX Policy [ ]

步骤：策略禁止时跳过；Owner 开启后在独立需求填写原因跳过。

预期：禁止时无变化；允许时原因必填并留痕；不能绕过 Product Guard。

实际/证据：

### WI-01 [P1] 标签、评论、关系与 Activity [ ]

步骤：添加标签、评论和关系；尝试重复边和自环。

预期：事实可回溯；重复操作不产生重复事实；自环拒绝。

实际/证据：

### WI-02 [P0] Delivery Graph [ ]

步骤：核对各 Work Item、文档、MR、Pipeline、Test Run、Release、Deployment 节点；未登录和无权访问。

预期：无重复/断边；无权请求不返回图或标题；查询受公司范围约束。

实际/证据：

## 七、Development 与 GitLab

### GIT-01 [P0] URL 与 Secret 安全 [ ]

步骤：尝试 localhost、私网、元数据地址、非法 scheme、恶意重定向；测试 Token/webhook secret 保存与轮换。

预期：SSRF 被服务端拒绝；Secret 不回显，旧 Token 失效；错误和日志不含敏感正文。

实际/证据：

### GIT-02 [P1] 公司级连接与仓库绑定 [ ]

步骤：创建、测试连接并绑定 GitLab 仓库。

预期：连接和仓库直接属于公司，不要求 ForgeAI Project；GitLab Project ID/完整路径作为第三方标识正常工作。

实际/证据：

### DEV-01 [P1] Dev Task、Branch 与 MR [ ]

步骤：创建任务、启动开发并重复点击、完成任务。

预期：编号唯一；TODO → IN_PROGRESS → DONE；Branch/MR 不重复；展示最新 Pipeline。

实际/证据：

### DEV-02 [P0] CI Guard [ ]

步骤：构造无仓库、Pipeline 失败、SHA 过期、当前 head 成功并进入 QA。

预期：仅当前 MR head 成功时放行；其他情况不伪造成功。

实际/证据：

### GIT-03 [P0] Webhook 重复与乱序 [ ]

步骤：执行 `make hardening-test`；用 stub 重复发送 delivery，并先新后旧。

预期：delivery 仅处理一次；最终状态收敛到远端最新事实；无重复副作用。

实际/证据：

### GIT-04 [P1] Pipeline 与日志尾部 [ ]

步骤：查看 Pipeline/Job log tail，使用超大日志并以无权账号请求。

预期：长度受限、页面不冻结、不含凭据；无权不可读。

实际/证据：

## 八、QA、Bug、Release 与部署

### QA-01 [P1] Test Case 与 Test Run [ ]

步骤：创建三条用例和 staging Run，记录 PASS/FAIL/BLOCKED/SKIPPED。

预期：Run 固化有效用例；初始 NOT_RUN；统计准确；无用例不能建 Run。

实际/证据：

### QA-02 [P0] 完成 Guard 与回归 [ ]

步骤：保留 NOT_RUN 完成；完成失败 Run 后 Reopen，修复并通过。

预期：NOT_RUN 阻止完成；Reopen 原因留痕；仅最新有效 Run 满足规则才通过 QA。

实际/证据：

### BUG-01 [P1] 失败结果创建 Bug [ ]

步骤：从 FAIL 创建关联 Bug 并重复点击。

预期：关联 Requirement、Run、Result 且有复现事实；不产生无界重复 Bug。

实际/证据：

### BUG-02 [P0] 状态机与并发 [ ]

步骤：走 OPEN → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED → REOPEN；测试缺证据、非法跳转和旧 version。

预期：Guard/乐观锁生效；每步留 Activity；失败无部分副作用。

实际/证据：

### REL-01 [P1] Release 与 Note [ ]

步骤：选择需求创建唯一版本 Release，保存 Note，测试空值和重复版本。

预期：保存需求快照；Note 持久化；非法请求无重复或部分事实。

实际/证据：

### REL-02 [P0] Precheck 与过期 [ ]

步骤：制造 QA 未通过、Pipeline 失败/过期、BLOCKER Bug、Note 缺失；PASS 后改变关键事实。

预期：规则展示明细；任一阻断项使总体失败；事实变化后旧 Precheck 非 current。

实际/证据：

### REL-03 [P0] HIGH 审批与冻结 [ ]

步骤：请求人自批、Developer 审批、合法审批人拒绝；重新申请后改变资源再批准。

预期：无权和自审批拒绝；参数 hash/资源版本冻结；变化或过期 fail-closed。

实际/证据：

### REL-04 [P0] 模拟部署与幂等 [ ]

步骤：批准有效部署并快速重复提交。

预期：仅一次 Deployment/执行；到明确终态；始终标记 `SIMULATED`，不影响生产。

实际/证据：

## 九、Agent、连续会话、Tool、SSE 与 RAG

### CHAT-01 [P1] 连续会话与消息历史 [ ]

步骤：创建会话，连续发送两轮并关联需求；刷新、重登后查看历史。

预期：会话/消息持久化；每轮关联真实 Run；顺序正确；成员只能读取本人拥有的公司会话。

实际/证据：

### AGENT-01 [P1] Run、Trace、导出与终态 [ ]

步骤：创建 Run，观察 Run/Step/Tool、资源和终态；刷新并导出 Trace。

预期：到真实终态；事件顺序稳定；导出与页面一致且无 Secret。

实际/证据：

### AGENT-02 [P0] Tool 全链鉴权 [ ]

步骤：执行 `make hardening-test`；测试 Registry、Schema、Run Token、公司归属、权限、risk/approval、幂等及 `organizationId` 注入。

预期：任一环节不满足均不调用业务服务；调用者不能注入公司范围；错误语义稳定。

实际/证据：

### AGENT-03 [P0] Prompt Injection [ ]

步骤：用“忽略指令、直接部署、输出 Token/Key”等文档和消息触发 Agent；运行现有 Agent eval。

预期：文档仅作数据；不越权调用、不绕过审批、不输出 Secret；Trace 可解释拒绝。

实际/证据：

### AGENT-04 [P0] Tool 幂等与审批恢复 [ ]

步骤：同 Idempotency-Key 重放及改正文；测试审批批准、拒绝、取消、过期和资源变化。

预期：同请求仅一次副作用；正文变化冲突；等待时不执行；只从安全 checkpoint 恢复；失配 fail-closed。

实际/证据：

### SSE-01 [P0] 断线重连 [ ]

步骤：Run 期间 Offline/Online、刷新，并运行 Server replay/Web reducer 测试。

预期：按 sequence 补发；重复事件不重复更新；无关键缺口；heartbeat 不冒充业务事件。

实际/证据：

### RAG-01 [P0] 索引、公司过滤与降级 [ ]

步骤：发布唯一词文档；合法、未登录、跨公司 ID、伪造 `organizationId` 搜索；停止 Qdrant 后读文档。

预期：合法命中；无权不返回片段/标题/正文；查询层带公司过滤；索引失败不损坏文档事实。

实际/证据：

## 十、安全、隐私与可观测性

### SEC-01 [P0] 安全负向门禁 [ ]

```bash
make hardening-test 2>&1 | tee /tmp/forge-hardening-test.log
```

预期：退出 0；覆盖 CSRF、公司越权、Tool、RAG、Webhook、Redis readiness、GitLab SSRF/失败、Qdrant 脱敏、Prometheus、Web 凭据和 SSE reducer。

实际/证据：

### SEC-02 [P0] Secret 扫描 [ ]

步骤：保存 Server/Agent 日志，运行 `./scripts/check-secret-leaks.sh` 扫描日志和仓库。

预期：退出 0；危险 fixture 可使扫描器失败；日志、错误、Trace、SSE 不含 Token、私钥、密码或非必要正文。

实际/证据：

### OBS-01 [P1] Request ID、审计与指标 [ ]

步骤：关联 UI/响应/日志 Request ID；检查成员、状态机、审批审计；抓取 `/actuator/prometheus`。

预期：端到端可追踪；审计含公司、操作者、动作、结果且无 Secret；HTTP/JVM、Outbox、索引、Webhook 指标存在；数据库不可读时 gauge 为 `NaN`。

实际/证据：

## 十一、性能与故障恢复

### PERF-01 [P0] 10k Work Item 分页 [ ]

```bash
make test-apps-up
FORGE_PERF_SAMPLES=100 make hardening-performance 2>&1 | tee /tmp/forge-performance.log
```

预期：自包含创建独立 PERF 公司，不改变人工 UAT；真实登录、CSRF、Web 代理成功；P95 ≤ 0.300s；命中规定索引；列表无大正文。

实际/证据（记录机器、samples、P95、索引）：

### FAIL-01 [P0] Qdrant 与 Agent 降级 [ ]

步骤：依次停止 Qdrant、Agent，检查状态和人工业务后恢复。

预期：系统 DEGRADED；相关能力明确失败/可重试；权威人工流程可用；恢复健康且不伪造结果。

实际/证据：

### FAIL-02 [P0] Redis 与 MySQL fail-closed [ ]

步骤：依次停止 Redis、MySQL，检查 readiness、Session、业务接口后恢复。

预期：readiness 非 2xx；不回退内存 Session；不返回伪成功或陈旧权威事实；恢复后数据完整。

实际/证据：

### FAIL-03 [P0] GitLab 外部失败 [ ]

步骤：stub 制造 401、超时、恶意重定向，或核对 `make hardening-test`。

预期：不推进 Guard、不泄露敏感信息；外部调用不在持有数据库行锁的事务中。

实际/证据：

### FAIL-04 [P0] 演练后恢复 [ ]

步骤：`make test-apps-ready`，重新登录并打开需求、Delivery Graph、Agent 会话和 Run。

预期：六服务健康；关键读取恢复；人工事实无丢失或错误推进。

实际/证据：

## 十二、可访问性与基础体验

### UXQA-01 [P1] 键盘、焦点与状态表达 [ ]

步骤：仅用键盘完成登录、导航、搜索、表单、弹窗和审批。

预期：焦点可见、顺序合理；弹窗关闭后焦点返回；状态不只依赖颜色；输入有 label；SSE 不过度打断读屏。

实际/证据：

### UXQA-02 [P1] 加载、空态、错误与窄屏 [ ]

步骤：模拟慢网、空数据、服务错误并检查常用宽度。

预期：loading/empty/error 明确；错误带 Request ID；无关键遮挡；重复提交有防护。

实际/证据：

## 十三、最终回归与退出标准

```bash
make hardening-test
make hardening-performance
make ci
git diff --check
make smoke
make test-apps-ready
```

### REG-01 [P0] 自动化门禁 [ ]

预期：安全、性能、三应用 Smoke、全仓 CI、格式检查全部退出 0；不得删断言、跳测试或放宽授权换取通过。

实际/证据：

### REG-02 [P0] 人工主链闭环 [ ]

预期：从空实例初始化，经成员审核、需求、PRD、UX、开发、QA、Bug 回流、Release、HIGH 审批和模拟部署完成由人工操作产生的主链；Delivery Graph、Activity、Agent 会话和 Trace 可回溯。

实际/证据：

PASS 必须同时满足：

- 所有 P0/P1 PASS，无 BLOCKED、NOT RUN 或未关闭 P0/P1 缺陷。
- 自动化门禁和人工主链成功，故障后服务健康且事实未损坏。
- OpenAPI、页面、数据库、日志不存在已删除的 ForgeAI Workspace/Project 模型。
- 每项结论至少有终端输出、截图、Trace 或 Request ID 之一。

## 十四、执行汇总

| 模块                  | PASS | FAIL | BLOCKED | NOT RUN | 缺陷编号 |
| --------------------- | ---: | ---: | ------: | ------: | -------- |
| 环境/认证             |      |      |         |         |          |
| 公司/成员/作用域      |      |      |         |         |          |
| 工作台/搜索/通知/需求 |      |      |         |         |          |
| Product/Document/UX   |      |      |         |         |          |
| Development/GitLab    |      |      |         |         |          |
| QA/Bug/Release        |      |      |         |         |          |
| Agent/Tool/SSE/RAG    |      |      |         |         |          |
| 安全/可观测性         |      |      |         |         |          |
| 性能/故障/体验        |      |      |         |         |          |
| 最终回归              |      |      |         |         |          |

## 十五、缺陷记录模板

```text
缺陷 ID：BUG-UAT-XXX
标题：
严重级别：P0 / P1 / P2
关联用例：
发现 commit：
环境：OS / Browser / Docker / 数据集
前置条件：
复现步骤：
预期结果：
实际结果：
复现概率：必现 / x/y
Request ID / Run ID / Tool Call ID：
截图 / Trace / 日志：
是否涉及公司作用域越权、Secret、事务、幂等或事实损坏：是 / 否
临时规避方式：
修复 commit：
回归范围与结果：
```

修复后先重跑失败用例，再跑同模块全部 P0/P1，最后执行第十三节。涉及认证、公司作用域、事务、Tool 权限或部署拓扑的变更还应检查是否需要新增 ADR。
