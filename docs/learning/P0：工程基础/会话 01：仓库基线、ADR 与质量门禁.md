# 会话 01：仓库基线、ADR 与质量门禁

## 我完成了什么

- 用户可见结果：建立 `forge-web`、`forge-server`、`forge-agent` 三应用的 Monorepo 基线，以及统一的 `Makefile`、CI、贡献约定和 ADR 目录。
- 开发者可以从仓库根目录通过 `make help` 查看统一命令，通过 `make ci` 执行阻断式质量门禁。
- 从入口到结果的调用链：开发者执行 `make ci` → `format-check`、`lint`、`contracts-check`、`test`、`build` → 各脚本进入对应模块执行检查 → 任一命令返回非零状态时，Make 和 CI 立即失败。

## 我理解的核心设计

- 关键不变量：三个正式应用只能命名为 `forge-web`、`forge-server`、`forge-agent`；仓库不能重新出现 `apps`、`backend`、`agent-service` 等旧目录。
- 关键不变量：质量门禁必须是阻断式的，不能只输出警告后继续通过。
- Monorepo 的主要价值不是把代码放在同一个目录，而是让三端代码、共享契约、部署配置和迁移在同一个 commit 中一起演进和回滚，降低契约漂移概率。
- 根 `Makefile` 只负责组织稳定的公开命令。复杂逻辑放入名称明确、可独立执行的脚本，避免 Makefile 变成难以测试和定位的脚本集合。
- ADR 记录的是具有长期影响的架构决策及其上下文、替代方案和后果，而不是复述当前代码。改变事实来源、认证、事务、Agent Tool 安全边界或部署拓扑时，需要先新增 ADR。
- 事务/一致性边界：本轮没有业务事务；这里建立的是仓库级一致性边界，即同一提交中的代码、契约、文档和部署定义应该相互匹配。
- 权限与安全边界：CI 权限保持只读仓库内容；仓库检查拒绝提交 `.env`、私钥等潜在 Secret 文件。
- 为什么没有选择三个独立仓库：P0 团队规模和部署边界尚不足以抵消多仓版本协调、跨仓 PR 和契约发布流程带来的成本。

## 失败路径

- 触发方式：删除必需目录、恢复旧目录名、遗漏指定 ADR 章节、加入可能包含 Secret 的文件，或者让任一格式、测试、构建命令失败。
- 系统如何失败：`scripts/check-repository.sh` 或对应质量脚本返回非零状态，`make ci` 停止，GitHub Actions 的 Quality Gates 任务失败。
- 数据是否保持正确：这些检查不修改业务数据；失败发生在合并或交付之前。
- 如何定位与恢复：从最先失败的 Make target 和脚本错误信息定位具体文件，修复后单独重跑该 target，最后重跑 `make ci`。

## 测试证据

- `tests/repository-baseline.sh`：验证仓库基线和根命令符合约定。
- `scripts/check-repository.sh`：验证必需文件/目录、正式命名、ADR 结构、Secret 文件和 Java 注释规则。
- `.github/workflows/quality-gates.yml`：在 push 和 pull request 上执行 `make ci`、三应用 Smoke 和 `git diff --check`。
- 它能证明什么：当前检出的仓库结构满足已编码的治理规则，并且门禁失败会阻止 CI 任务成功。
- 它不能证明什么：不能证明所有架构决策都合理，也不能识别内容看似合规但语义错误的 ADR，更不能保证开发者本机未跟踪的 Secret 没有泄露到其他位置。

## 仍不清楚的问题

- 当某项架构变化只是现有 ADR 的实现细节时，应该更新原 ADR，还是新增一份 superseding ADR？
- 后续三应用发布节奏分化后，Monorepo 是否仍使用单一版本号，还是分别管理应用版本？
