# ADR-014：本地开发使用 Docker Desktop 运行依赖服务

- 状态：Accepted
- 日期：2026-09-02
- 补充：ADR-010 的本地开发环境策略

## 上下文

ForgeAI 当前处于分阶段开发与面试演示阶段。开发机器已经安装 Docker Desktop，本地开发不再需要将 MySQL、Redis、Qdrant 或其他依赖部署到 VM/腾讯云 CVM。将开发期基础设施放在远程 VM 会增加网络、凭据、费用和排障复杂度，也不利于每个开发会话独立复现。

## 决策

开发、测试、黄金 Demo 和本地验收全部使用本机 Docker Desktop + Docker Compose：

- `forge-web`、`forge-server`、`forge-agent`、Worker（如启用）、MySQL、Redis、Qdrant 和本地入口代理均运行在同一台开发机的 Compose 网络中。
- MySQL、Redis、Qdrant、Agent 和 Worker 默认不发布宿主机端口；只允许本机浏览器访问的入口代理映射到 `127.0.0.1`。
- Compose 数据卷保存 MySQL、Qdrant 和附件数据；停止或重建容器不得删除命名卷，除非开发者明确执行清理操作。
- 本地默认访问地址为 `http://localhost:<port>`；MVP 不要求域名、公开 HTTPS、Nginx 证书或云安全组。
- GitLab 与模型 Provider 可以是外部服务，但其 Token/API Key 仍通过本机 `.env`/Secret 文件注入，不提交仓库。

最终上线仍使用腾讯云 CVM 上的 Docker Compose + Nginx/TLS，生产 MySQL、Redis、Qdrant 运行在该服务器的私有 Docker 网络或按生产方案替换为托管服务。云端部署不是每轮本地开发会话的前置条件，但属于最终上线验收与交付范围。

## 后果

- 每位开发者可使用同一份 Compose 配置在本机复现环境，降低分阶段会话的启动成本。
- Docker Desktop 必须分配足够 CPU、内存和磁盘；资源不足时首先缩减并发服务或使用 Mock Provider，而不是把数据库迁回 VM。
- 本机命名卷需要纳入备份、恢复和清理说明；`docker compose down -v` 被视为破坏性开发操作。
- 本地 Docker Compose 与腾讯云生产 Compose 保持服务名、环境变量和数据卷语义一致；最终上线前必须在 CVM 完成部署、备份恢复和 HTTPS 验证。

## 替代方案

- 继续使用 VM 部署基础设施：不采用，当前阶段增加不必要的远程运维与网络依赖。
- 在宿主机直接安装 MySQL、Redis、Qdrant：不采用，环境漂移和清理成本更高。
- Kubernetes：不采用，超出 MVP 的学习与维护范围。
