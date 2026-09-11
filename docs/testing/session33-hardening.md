# 会话 33：安全、可靠性、性能与故障演练

本手册只覆盖会话 33。自动门禁不改变本地服务状态；性能验收写入独立的 `PERF` 项目；故障演练会依次停止并恢复 Compose 服务，必须在无人共用的本地环境执行。

## 一、自动加固门禁

```bash
make hardening-test
```

该命令依次验证：

- CSRF、Workspace/Project 隔离、越权 Tool、RAG 权限过滤和重复投递；
- Redis readiness fail-closed；
- GitLab URL、超时、401 和重定向边界；
- Qdrant 错误响应不进入异常消息；
- Prometheus 指标端点与可靠性 backlog 指标；
- Web 请求凭据策略和 SSE 重放去重；
- 仓库及指定日志文件中的高置信 Secret 格式。

单独扫描运行日志时：

```bash
docker compose --env-file deploy/.env -f deploy/compose.yml -f deploy/compose-test.yml logs --no-color forge-server forge-agent > /tmp/forge-session33.log
./scripts/check-secret-leaks.sh /tmp/forge-session33.log
```

扫描命中会返回非零退出码；读取错误同样返回非零，不会被当成“没有泄露”。规则只覆盖高置信 Token、Bearer JWT 和私钥头，不能替代凭据轮换与人工审阅。

## 二、10k Work Item 性能验收

先启动已构建的三应用：

```bash
make test-apps-up
make hardening-performance
```

脚本会幂等创建独立的 `ForgeAI Performance` 公司和测试 Owner，并写入 10,000 条 Work Item。随后执行真实列表 SQL 的 `EXPLAIN`，要求选择 `idx_work_items_scope_type_status_page`；再通过真实 CSRF、登录 Session 和 Web 代理采集 100 次、每页 100 条的列表请求。性能夹具不依赖人工验收数据，也不会写入人工验收公司。

默认门槛是 P95 不超过 `0.300s`，并断言列表响应不含 `description` 正文。慢速机器可用环境变量改变采样数，但正式验收不应放宽预算：

```bash
FORGE_PERF_SAMPLES=200 make hardening-performance
```

输出示例：

```text
Work Item 列表：samples=100 P95=0.042000s budget=0.300s
EXPLAIN：idx_work_items_scope_type_status_page
```

性能数据会保留到下一次数据库重建，便于重复采样；它不修改黄金项目的交付链事实。

## 三、故障演练

警告：以下命令会停止本机 Compose 中的 MySQL、Redis、Qdrant 和 Agent，再逐项恢复。不要在共享环境执行。

```bash
make test-apps-up
make hardening-faults
```

| 故障注入 | 预期行为 | 恢复判据 |
| --- | --- | --- |
| Qdrant 停止 | Server 为 `DEGRADED`，人工主流程入口仍返回成功 | Qdrant 恢复健康 |
| Agent 停止 | Server 为 `DEGRADED`，人工主流程入口仍返回成功 | Agent 恢复健康 |
| Redis 停止 | readiness 非 2xx，实例 fail-closed | Redis 恢复健康 |
| MySQL 停止 | readiness 非 2xx，实例 fail-closed | MySQL 恢复健康 |
| GitLab 超时/401/恶意重定向 | 调用失败且不伪造成功，不泄露 Token | `GitLabHttpClientTest` 通过 |

脚本注册了退出恢复钩子；即使断言失败也会尝试重新启动全部服务。完成后仍应人工确认：

```bash
make test-apps-ready
```

## 四、指标面板与告警基线

Prometheus 抓取入口为 `forge-server:8080/actuator/prometheus`。本轮提供指标和查询基线，不实现下一会话的发布编排或生产监控部署。

| 目标 | PromQL | 建议告警 |
| --- | --- | --- |
| Work Item 列表 P95 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/api/v1/work-items"}[5m])))` | 连续 10 分钟超过 `0.3` |
| HTTP 5xx 比例 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | 连续 5 分钟超过 `0.01` |
| 待投递 Outbox | `forge_outbox_pending` | 持续增长或超过运行基线 |
| 最老 Outbox 年龄 | `forge_outbox_oldest_age_seconds` | 超过 `300` |
| 文档索引死信 | `forge_document_index_dead` | 大于 `0` |
| Webhook 死信 | `forge_webhook_dead` | 大于 `0` |

数据库不可读时，自定义 gauge 返回 `NaN`，避免把采集失败误报成零积压。MySQL 是这些可靠性事实的唯一来源；Prometheus 只采集快照，不参与业务事务。

## 五、人工验收清单

- [ ] `make hardening-test` 全部通过。
- [ ] 10k 数据下列表 `EXPLAIN` 命中新索引。
- [ ] 100 次真实请求 P95 小于等于 300ms。
- [ ] 列表响应不包含 Work Item `description`。
- [ ] Prometheus 可见 HTTP histogram、JVM 和四个可靠性指标。
- [ ] Qdrant/Agent 故障时人工主流程可用且状态降级。
- [ ] Redis/MySQL 故障时 readiness 拒绝流量。
- [ ] GitLab 超时、401 和恶意重定向均明确失败。
- [ ] 应用日志高置信 Secret 扫描通过。
- [ ] 演练后所有 Compose 服务恢复健康。
