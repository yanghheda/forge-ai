# packages

共享生成物与契约目录。`forge-contracts` 保存 Tool/Skill JSON Schema 与版本化契约；契约由 Agent 校验命令读取，但 Tool 的实际权限、资源范围和事务仍由 `forge-server` 执行。

跨应用生成物和受治理的共享包目录。预计包含 OpenAPI 生成契约、UI 包和共享配置；不得在这里手工维护三端重复 DTO，也不得演变为无边界的通用代码目录。

具体包随对应契约或应用会话建立。
