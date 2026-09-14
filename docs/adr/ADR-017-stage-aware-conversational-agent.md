# ADR-017：阶段感知的角色会话驱动交付流程

- 状态：Accepted
- 日期：2026-09-13

## 上下文

PRD 要求 Product、UX、Development、QA、Release 围绕同一 Requirement 使用 Agent 完成交付，
并允许 Agent 建议或发起状态迁移。当前指令中心固定启动 Product Skill；会话不绑定 Requirement，
模型只收到 Tool 名称而没有契约 Schema，也没有提交阶段迁移的 Tool，因此无法形成五角色闭环。

模型输出属于不可信输入。阶段目标状态、权限、准入材料、QA 结论和发布审批都不能由模型决定。

## 决策

1. Agent 会话可绑定一个 Requirement；未绑定会话只允许 Product 创建 Requirement。创建成功后服务端将
   会话绑定到该 Requirement，后续消息沿用该范围。
2. 绑定后的默认 Skill 由 `forge-server` 根据 Requirement 当前状态确定。客户端可以展示角色，但不能指定
   与当前阶段不一致的 Skill 来扩大 Tool 集合。
3. Manifest 向模型提供裁剪后的 Tool 名称、说明和输入 JSON Schema。它们只用于生成结构化调用，不构成授权。
4. 各角色通过小而明确的阶段动作 Tool 发起迁移；执行复用 `RequirementTransitionService`，由其解析目标
   状态并执行权限、版本、幂等和 Guard 校验。
5. Product/UX Review、QA Guard、Release Precheck 和 HIGH Deployment Approval 保持现有确定性或人工边界。
   Agent 可以发起、解释缺失项和等待，不能伪造通过结果。
6. 每条消息创建独立 Run；会话保存用户消息与 Run 最终摘要。业务上下文来自绑定 Requirement 和服务端事实，
   不把历史自然语言直接当作授权或状态事实。
7. Command Center 与角色页面使用同一会话/Run API，并展示真实计划、状态、Tool、审批和结果。

## 后果

- 用户可以在当前角色页面用自然语言推进流程，而无需理解内部状态机 Action。
- 阶段变化仍走人工入口相同的应用服务，因此 Agent 不会成为旁路授权通道。
- Guard 未满足时模型只能说明缺失材料。
- AI 生成高质量业务内容可以独立增强，不阻塞流程编排主线。

## 替代方案

- 由模型直接写下一状态：拒绝，因为绕过权限、Guard、乐观锁和审计。
- 一个无人值守的超级 Agent 自动跑完全流程：拒绝，因为角色责任、评审与 HIGH 审批必须保留。
- 每个页面实现独立聊天后端：拒绝，因为会产生不同的权限和 Trace 语义。
