# 会话 21：Tool Registry 与 Product/UX Tool

## 我完成了什么

- 在 `packages/forge-contracts` 建立首批 8 个版本化 Tool 契约与 Product、UX Skill 契约，CI、forge-server 和 forge-agent 共用这一事实来源。
- forge-server 启动时加载并校验 Registry，创建 Run 时按 Skill 下发 `effectiveToolNames` 与调用上限；执行时再次按 Registry、Schema、Run/Skill、scope、最新权限、风险策略和幂等键逐层裁决。
- 新增 run-scoped credential 校验与 `/internal/v1/tools/{toolName}:execute`，模型不能提交 `workspaceId` 或 `projectId`，服务端只从已签名凭据和 Run 事实恢复 scope。
- LOW Tool 支持项目、工作项、交付图和权限过滤 RAG 读取；MEDIUM Tool 支持创建 Requirement、PRD、UX Task 和 UX 文档，并复用现有应用服务。
- `agent_tool_calls` 保存成功写调用的 Tool 版本、冻结参数和结构化结果。业务副作用与成功记录在同一事务提交；同一幂等键只重放相同 Tool、版本和参数。
- forge-agent 图新增 select → guard → execute → observe 循环，通过受控 Transport 回调 Server；每次调用使用稳定且不重复的 `call-N`，最终答案只依据结构化观察。

## 我理解的核心设计

- JSON Schema 只回答“参数形状是否合法”，不回答“当前用户能不能操作这个资源”。授权必须由 forge-server 根据 Run 发起人、真实资源 scope 和最新 RBAC 事实重新计算。
- Manifest 的 Tool 白名单是 Agent 入口上限，不是最终权限快照。用户在 Run 创建后被撤权，Server 执行 Tool 时仍会拒绝。
- Agent 选择 Tool 只是意图；只有 Backend 返回 `SUCCEEDED` 才表示业务副作用已经提交。自然语言中的“已创建”没有权威性。
- `runId + toolCallId` 是写 Tool 的幂等键。Tool 名称、契约版本和参数属于这个键的冻结语义；换参数复用键必须报冲突，不能返回与当前意图不匹配的旧结果。
- MEDIUM 的 `ALLOW/ASK/DENY` 是本轮简化策略。`ASK` 只返回 `PENDING_CONFIRMATION`，正式审批持久化、冻结资源版本与 checkpoint 恢复属于会话 22。

## 安全与事务边界

- 内部端点不依赖浏览器 CSRF/Origin，而依赖短时签名 credential；credential 只携带 Run 和 scope 标识，不携带可信权限集合。
- Tool 参数中的资源 ID 仍由应用服务使用显式 `workspace_id`、`project_id` 查询，跨项目父 Requirement 会按不存在处理。
- MEDIUM 写 Tool 使用一个本地事务包住应用服务副作用与 `agent_tool_calls` 记录。若幂等记录失败，业务写同时回滚，避免“资源已创建但没有可重放结果”的故障窗口。
- 并发重复调用由数据库唯一键裁决：唯一事务提交，竞争失败事务回滚后读取赢家的结构化结果。
- `search_documents` 仍调用会话 20 的 RAG 门面，Workspace/Project metadata filter 由服务端构造，Agent 只能提供 query、documentType 和 topK。

## 测试证据

- 契约测试覆盖真实仓库加载、Skill 白名单、未知 Tool 引用、重复契约和非法 YAML；`make contracts-check` 校验必填元数据及 Backend mapping。
- Agent 测试覆盖 run token 透传、Tool 成功/待确认/拒绝/网络失败、Manifest 白名单、调用预算、checkpoint 后不重复执行，以及多 Tool 调用获得不同稳定 ID。
- Server 集成测试覆盖缺失/伪造/过期 credential、Run scope、终态 Run、Schema、调用方注入 scope 参数、Skill 禁用 Tool、权限撤回、LOW 读取、MEDIUM 三种策略、结构化结果和幂等重放。
- Work Item 集成测试覆盖 UX Task 的父子关系、跨 scope/错误父类型和 `ux.create` 撤权。

## 本轮边界

- 未实现审批表、批准/拒绝、参数与资源版本冻结、WAITING_APPROVAL checkpoint 恢复；这些属于会话 22。
- Fake LLM 只提供确定性选择路径，不代表真实模型质量；它用于证明 Guard、Transport、执行结果观察和恢复语义。
- 首批 Tool 没有开放更新、GitLab、QA、Release 或 HIGH 风险操作，避免提前进入后续会话。

## 3 个复盘问题

1. 为什么 JSON Schema 通过后仍必须由 Backend 做最新权限和资源 scope 校验？
2. 为什么同一 `toolCallId` 换参数时应该报冲突，而不是直接重放第一次结果或再次执行？
3. Agent 已输出“创建成功”，但 Backend 没有返回 `SUCCEEDED` 时，系统应该相信哪一方，为什么？
