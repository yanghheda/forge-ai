# 黄金 Demo 与端到端回归

会话 32 固定使用“增加手机号验证码登录”样例。数据只占用 `32000-32999` ID 段，GitLab 和 LLM 使用确定性演示实现，部署仅为 `SIMULATED`。

## 运行方式

```bash
make apps-up
make demo-e2e
```

仅重置样例事实时执行 `make demo-seed`。样例入口为 `workspace=demo` / `project=DEMO` / `requirement=DEMO-1`。本地账号为 `owner@demo.forgeai.local`，密码为 `ForgeAI-Demo-2026!`；不得将该凭据迁移到生产环境。

Playwright 默认在失败时保留截图和 Trace。如需每次录像，执行 `PLAYWRIGHT_VIDEO=1 make demo-e2e`。新环境须先执行 `npx playwright install chromium`。

## 回归范围

正向链从真实登录会话验证 Requirement、PRD、UX、Dev Task、MR、Pipeline、Test Case/Run、Bug、Release、Deployment 和 Agent Trace。

`make golden-regression` 运行九类负向门禁：

| 场景 | 权威回归点 |
| --- | --- |
| 缺少 PRD 评审 | Requirement transition guard |
| 缺少 UX 就进入开发 | Requirement/UX guard |
| Pipeline 失败却提交 QA | Development QA guard |
| 存在 BLOCKER Bug 却发布 | Release precheck registry |
| 跨 Workspace 读取 | Agent Run scope integration |
| Developer 代替批准 Release | Tool permission/skill allowlist |
| Prompt Injection 诱导部署 | Tool manifest allowlist |
| Webhook/Tool 重复投递 | Webhook persistence + Tool idempotency |
| SSE 断线重连 | Server replay + Web reducer sequence |

Eval 数据位于 `forge-agent/tests/evals/golden-demo.jsonl`。门禁依赖 Tool 集合、权限、审批预期、错误码和输出结构，不使用随机文案作为阻断条件。
