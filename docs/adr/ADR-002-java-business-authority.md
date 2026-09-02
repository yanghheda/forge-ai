# ADR-002：Java 为业务权威，Python 为独立 Agent Service

- 状态：Accepted
- 日期：2026-09-02

## 上下文

模型输出、用户文档和外部系统响应均是不可信输入。Agent 擅长理解、规划、检索和编排，但不适合作为权限、状态机、事务或持久化规则的最终执行者。与此同时，Python 的 AI 生态适合承载 LangGraph、模型适配与评测，不应迫使业务系统与 Agent Runtime 使用同一技术栈。

## 决策

`forge-server` 是业务数据、认证授权、状态机、事务、审计和外部集成的唯一权威。`forge-agent` 是独立 Python 服务，只接受 Server 发起的内部 Run 请求，并通过声明权限和风险等级的受控 Tool API 回调 Server。

Server 在 Tool 执行点根据原始用户、Workspace、Run Context 和当前资源版本重新鉴权。Agent 不直接连接业务 MySQL，不自报权限，不持有 GitLab Token、部署凭据或其他业务 Secret。

## 后果

- 人工请求与 Agent 请求复用同一套确定性业务规则和授权能力。
- Agent Runtime 可替换或升级，而不改变业务事实来源。
- 服务间调用、短时凭据、幂等、审计和失败恢复成为必须实现的边界。
- Agent 故障可以降级为人工流程，但跨服务操作只保证设计好的最终一致性。

## 替代方案

- Agent 直接写业务数据库：拒绝，因为会绕过授权、事务、审计和领域规则。
- 在 Java 进程内嵌全部 Agent Runtime：暂不采用，因为会耦合 AI 生态与业务发布周期。
- 让 Web 直接调用 Agent：拒绝，因为会绕过 Server 的会话、范围和审计边界。

