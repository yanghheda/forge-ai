# ForgeAI 设计与开发文档

目录导航：

1. [`design/ForgeAI_公司级架构基线_v2.md`](design/ForgeAI_公司级架构基线_v2.md)：当前产品与工程事实基线。
2. [`design/`](design/) 其余 v1 文档（PRD、完整技术方案、详细设计）：历史基线，仅在追溯旧功能时读取。
3. [`adr/`](adr/README.md)：架构决策记录，是基线的最新增量。

实现若需要改变既定安全边界、事实来源、状态模型、权限模型或部署拓扑，应先新增 ADR，再同步相关文档与测试。

任务应先使用 [`context/INDEX.md`](context/INDEX.md) 把目标路由到最小必读章节，不默认全文读取基线文档。
