# 贡献指南

## 开始之前

1. 阅读 `README.md`、`AGENTS.md` 和当前开发会话对应的设计章节。
2. 使用 `make help` 查看公开开发命令。
3. 将改动限制在当前会话或议题范围内，保护工作树中的既有改动。

## 开发约定

- 分支和提交应聚焦单一逻辑增量。
- 推荐使用 Conventional Commits，例如 `chore(repo): bootstrap monorepo quality gates`。
- 契约源文件和生成物必须在同一个 commit 中更新；禁止直接手改生成 Client。
- 涉及安全边界或关键架构选择时，先在 `docs/adr/` 记录决策。
- 依赖锁文件、Migration、权限配置、生成代码和 CI 变更必须人工审查。

## 提交前检查

```bash
make ci
git diff --check
```

任何失败都必须解决或如实报告，禁止用忽略错误的方式让流水线变绿。

## ADR 触发条件

以下变化必须新增 ADR：事实数据库、会话或向量库变更；引入消息队列或微服务；认证模式变化；Work Item/Release 建模变化；Agent Tool 安全边界变化；SSE 改为其他实时协议；新增 SCM；自托管拓扑变化。

