# Fake Agent 黄金流程

该流程用于在不调用真实模型时验证 Web、Server、Agent、Tool、审批、Checkpoint、SSE 与业务 Guard。
Fake 只生成确定性 Tool 参数；业务是否成功始终由 `forge-server` 决定。

## 使用条件

- `FORGE_AGENT_MODEL_PROVIDER=fake`
- 使用具备全流程权限的本机 Owner 账号
- Agent 与 Server 已重启到当前代码

## 对话指令与人工停点

| 阶段 | 对话指令 | Fake Tool | 完成后动作 |
|---|---|---|---|
| Product | `新建` | `create_requirement` | 在对话中确认 MEDIUM 操作 |
| Product | `创建 PRD` | `create_prd_document` | 在文档页面补正文并发布 PRD |
| Product | `推进当前阶段` | 读取 Requirement 后提交或批准 Product Review | 在对话中确认 MEDIUM 操作 |
| UX | `创建 UX 任务` | `create_ux_task` | 在对话中确认 |
| UX | `创建 UX 文档` | `create_ux_document` | 补正文并发布 UX Spec |
| UX | `推进当前阶段` | 提交或批准 UX Review | 确认后继续 |
| Developer | `创建技术设计` | `create_tech_design` | 补正文并发布 |
| Developer | `创建开发任务` | `create_dev_task` | 配置 GitLab、负责人并从研发页面启动开发 |
| Developer | `推进当前阶段` | `SUBMIT_FOR_QA` | 仅在 Dev Task、MR、Pipeline Guard 满足后成功 |
| QA | `创建测试用例` | `create_test_case` | 人工创建/执行 Test Run 并记录结果 |
| QA | `推进当前阶段` | `START_QA` 或 `QA_PASS` | PASS 只能来自 Server 已保存的测试事实 |
| QA | `创建缺陷` | `create_bug` | 按需验证失败回流 |
| Release | `创建发布并预检` | `create_release` → `run_release_precheck` | Precheck PASS 后进入 HIGH 审批与模拟部署 |

## 不可自动伪造的事实

文档发布、评审责任、GitLab Pipeline、Test Run 结果和 HIGH 双人审批必须由真实业务入口产生。
Fake 遇到 Guard 失败时保留失败 Tool Trace；补齐事实后应发送新的阶段指令，不修改旧 Run 结果。
