# ForgeAI 仓库协作规则

## 适用范围

本文件适用于整个仓库。更深目录若存在自己的 `AGENTS.md`，可以补充模块规则，但不得放宽这里定义的架构与安全边界。

## 阶段边界

- 开发严格遵守 `docs/development/ForgeAI_分阶段开发指南_v1.0.md` 的会话范围。
- 不为方便而提前实现后续会话内容；发现跨阶段问题时记录为遗留项。
- 设计若改变事实来源、认证、事务、Agent Tool 安全边界或部署拓扑，必须先新增 ADR。

## 正式命名

三个应用只使用 `forge-web`、`forge-server`、`forge-agent`。不得重新引入 `apps/web`、`backend`、`agent-service` 等旧名称。

## 架构边界

- `forge-server` 是业务事实、权限、状态机和事务的唯一权威。
- `forge-web` 不直接连接 Agent、MySQL、Redis、Qdrant 或 GitLab。
- `forge-agent` 不直接写业务数据库，不持有 GitLab Token 或部署凭据，只通过受控 Tool API 调用 Server。
- MySQL 是事实来源；Redis 是可丢失的会话/短期状态；Qdrant 是可重建的派生索引。
- 外部系统调用不得放在持有数据库行锁的事务中。

## 持久化约定

- 生产代码统一使用 MyBatis 体系访问 MySQL：简单单表 CRUD 使用 MyBatis-Plus；带租户 scope、多表聚合、锁或性能敏感条件的查询使用 `@Mapper` 中可审查的 MyBatis SQL。
- 不使用或新增 `JdbcTemplate` 生产 Store；测试可使用它直接搭建数据库事实和断言结果，但不得借此绕过生产 Mapper。
- 所有 Mapper SQL 仍必须显式携带适用的 `workspace_id`、`project_id` 等 scope 条件；改用 MyBatis 不得弱化授权或租户隔离边界。

## 注释规则

- 解释性注释优先使用中文；标识符、协议名、第三方字段和对外英文文案保持原样。
- 除生成源码外，Java 生产代码中的每个普通字段、`record` 组件、`enum` 常量和 enum 成员字段，都必须分别具有紧邻声明的中文普通块注释（`/* ... */`）。
- 上述成员含义注释不得使用 Javadoc（`/** ... */`）替代。
- 注释应说明业务语义、范围或约束，不能只复述字段名。
- Swagger/OpenAPI 注解只允许出现在 Controller，不以领域类型注释生成 API 文档。

## 数据库注释规则

- 自编写的 Flyway 建表语句必须为每张表和每个字段分别声明中文 `COMMENT`；注释应说明业务语义、范围、空值含义或约束，不能只复述表名或字段名。
- 后续迁移新增或修改字段时必须同时维护字段 `COMMENT`；不得以 SQL 行注释（`-- ...`）替代写入 MySQL 元数据的表/字段 `COMMENT`。
- 已发布迁移仍然只允许前进；若已发布对象缺少或需要修订注释，必须通过新的 Flyway 迁移使用 `ALTER TABLE` 修正，不得回改历史脚本。

## 实施与验证

- 先写或更新能表达规则的测试，再实现最小代码使其通过。
- 每完成一个逻辑单元就运行对应测试，不删除断言、不放宽边界、不吞掉失败。
- 根目录命令保持为薄封装；复杂逻辑放在名称明确、可独立执行的脚本或模块任务中。
- 提交前至少运行 `make ci` 和 `git diff --check`。
- 不提交真实 Secret、个人环境配置、构建产物或编辑器缓存。
