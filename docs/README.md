# ForgeAI 设计与开发文档

建议按以下顺序阅读：

1. [`design/ForgeAI_PRD_v0.2.md`](design/ForgeAI_PRD_v0.2.md)：产品目标、范围、用户旅程与验收基线。
2. [`design/ForgeAI_完整技术方案_v1.2.md`](design/ForgeAI_完整技术方案_v1.2.md)：总体技术选型、架构原则与大阶段计划。
3. [`design/ForgeAI_详细设计_v1.0.md`](design/ForgeAI_详细设计_v1.0.md)：可编码的数据、状态机、权限、API、Agent、SSE、GitLab、测试与部署设计。
4. [`development/ForgeAI_分阶段开发指南_v1.0.md`](development/ForgeAI_分阶段开发指南_v1.0.md)：34 个逐轮开发会话、学习目标、Codex 边界和验收方法。

执行时以详细设计作为稳定工程基线，以分阶段开发指南控制每轮范围。实现若需要改变既定安全边界、事实来源、状态模型或部署拓扑，应先新增 ADR，再同步相关文档与测试。
