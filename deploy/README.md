# deploy

自托管部署资产目录。部署配置必须通过环境变量或 Secret 文件注入凭据，不得提交真实 Secret。

## 环境分层

- 宿主机开发：`compose.yml + compose.host-dev.yml`，使用 `make host-infra-*`。
- 测试与 Demo：`compose.yml + compose-test.yml`，使用 `make test-apps-*`。
- 正式发布：仅使用 `compose.yml`，使用 `make apps-*`。

`compose-test.yml` 使用独立的 `forge-ai-test` Compose project、容器、网络和命名卷，
不会复用正式环境数据。测试 Web 默认使用 `127.0.0.1:13000`；数据端口只绑定 `127.0.0.1`：MySQL `3306`、Redis
`6379`、Qdrant HTTP `6333` 和 gRPC `6334`。如端口冲突，可在 `deploy/.env`
设置 `FORGE_TEST_WEB_PORT`、`FORGE_TEST_MYSQL_HOST_PORT`、`FORGE_TEST_REDIS_HOST_PORT`、
`FORGE_TEST_QDRANT_HTTP_HOST_PORT`、`FORGE_TEST_QDRANT_GRPC_HOST_PORT`。

## 正式环境

Compose 默认只启动 MySQL、Redis 和 Qdrant；`applications` profile 额外启动三应用。首次启动前复制示例配置，并更换本机数据库密码和 Agent JWT 签名密钥：

```bash
cp deploy/.env.example deploy/.env
make infra-up
make infra-ready
```

使用正式配置启动三应用：

```bash
make apps-up
make apps-ready
```

只有 `forge-web` 映射到宿主机 `127.0.0.1:3000`。Server、Agent 与三项数据设施只加入 `forge-internal` Compose 网络，不映射宿主机端口。Server 默认使用 `prod` profile 和真实 GitLab provider。MySQL 与 Qdrant 使用命名卷；Redis 明确作为可丢失的短期状态运行。`make infra-down` 和 `make apps-down` 都会保留数据卷。

## 测试环境

```bash
make test-apps-up
make test-apps-ready
```

测试结束后使用 `make test-apps-down`，默认保留测试数据卷。测试环境 Server 使用
`test` profile 和确定性 demo GitLab provider。

`make smoke` 使用示例配置在 `127.0.0.1:13000` 临时启动完整拓扑，验证 Web → Server → Agent 调用及无效 Agent 凭据拒绝，完成后自动停止且不删除数据卷。

不要把 `deploy/.env` 提交到 Git。确需从宿主机 IDE 调试数据服务时，可自行创建不提交的 Compose override 文件。`docker compose down -v` 会删除 MySQL/Qdrant 本机数据，只能在明确重置环境时手工执行。

## 三应用在宿主机运行

仓库提供 `deploy/compose.host-dev.yml.example`，本机私有副本为已忽略的
`deploy/compose.host-dev.yml`。它只将 MySQL、Redis、Qdrant 绑定到
`127.0.0.1`，不会启动三个应用：

```bash
cp deploy/.env.example deploy/.env             # 首次使用时执行并更换 Secret
cp deploy/compose.host-dev.yml.example deploy/compose.host-dev.yml
make host-infra-up
make host-infra-ready
```

如果默认端口被占用，可在 `deploy/.env` 中设置 `FORGE_MYSQL_HOST_PORT`、
`FORGE_REDIS_HOST_PORT`、`FORGE_QDRANT_HOST_PORT`，并同步修改下方应用配置。

IDEA 的 `ForgeServerApplication` Run Configuration 至少需要以下环境变量：

```text
FORGE_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/forge_ai
FORGE_MYSQL_USERNAME=forge_ai
FORGE_MYSQL_PASSWORD=<deploy/.env 中的本机密码>
FORGE_REDIS_HOST=127.0.0.1
FORGE_REDIS_PORT=6379
FORGE_QDRANT_BASE_URL=http://127.0.0.1:6333
FORGE_AGENT_BASE_URL=http://127.0.0.1:8000
FORGE_AGENT_INTERNAL_JWT_SECRET=<与 forge-agent 相同的本机随机密钥>
```

`forge-agent` 在宿主机启动时使用同一个 `FORGE_AGENT_INTERNAL_JWT_SECRET`。
停止基础设施使用 `make host-infra-down`，该命令保留 MySQL/Qdrant 数据卷。

三个应用在宿主机运行时，Server 使用 `dev,local` profiles。Compose Smoke 显式使用
`test` profile，容器服务名来自 `application-test.yml` 或 Compose 环境变量，不会加载
个人的 `application-local.yml`。生产 `prod` profile 留待部署阶段配置。
