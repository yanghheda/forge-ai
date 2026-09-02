# ADR-011：OpenAPI 为跨端 REST 契约来源

- 状态：Accepted
- 日期：2026-09-02

## 上下文

ForgeAI 的 Web、Server 和 Agent 使用不同语言与工具链。如果三端分别手写相同 DTO、路径和错误结构，契约容易在独立演进中漂移。Monorepo 只有配合单一契约来源和生成检查，才能真正保证同一 commit 下的兼容性。

Tool Contract 还包含权限、风险和审批语义，不能简单等同于公开 REST DTO。

## 决策

`forge-server` 产出的 OpenAPI 文档是外部 REST 契约的事实来源。TypeScript Client 和必要的 Python DTO 从该契约生成并存放在受治理的契约包中。源契约与生成物必须在同一个 commit 更新，CI 检查生成差异，禁止直接修改生成 Client。

Agent Tool 继续使用独立 JSON Schema/Pydantic Contract，并显式映射到 Backend Endpoint、权限、风险和业务需求。

## 后果

- 路径、请求、响应和错误模型能够跨端一致演进。
- Server 的契约变更会尽早暴露 Web/Agent 兼容问题。
- 生成工具版本和输出必须锁定，生成物边界必须清晰。
- OpenAPI 不能替代领域测试、权限测试或 Tool 安全契约。

## 替代方案

- 三端手写 DTO：拒绝，因为会产生重复定义和契约漂移。
- 由前端类型反向定义 Server：不采用，因为业务权威位于 Server。
- 将 Tool Contract 完全并入 OpenAPI：不采用，因为 Tool 还需要独立的风险、审批和上下文约束。

