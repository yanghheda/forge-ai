# deploy

自托管部署资产目录。部署配置必须通过环境变量或 Secret 文件注入凭据，不得提交真实 Secret。

## 环境分层

- 服务器共享基础设施：已在项目外的 `/opt/infra` 独立部署，本仓库不创建、停止或升级它。
- 测试与 Demo：`compose.yml + compose-test.yml`，只启动三应用并连接现有 `infra-net`。
- 正式发布：仅使用 `compose.yml`，只管理 ForgeAI 三应用。

`compose.yml` 中不存在 MySQL、Redis、Qdrant service 或数据卷。`infra-net` 必须由
`/opt/infra` 预先创建，且网络内可通过 `mysql`、`redis`、`qdrant` 服务名访问对应服务。

## 正式环境

首次启动前复制示例配置，填入 `/opt/infra` 已为 ForgeAI 分配的数据库账号，并更换 Agent JWT 签名密钥。
`make apps-up` 只拉取已发布镜像，不在生产服务器构建源码：

```bash
cp deploy/.env.example deploy/.env
make apps-up
make apps-ready
```

只有 `forge-web` 映射到宿主机 `127.0.0.1:3000`。`forge-server` 同时加入项目私有网络和
外部 `infra-net`，`forge-web` 与 `forge-agent` 不直接接入基础设施网络。Server 默认使用
`prod` profile 和真实 GitLab provider。`make apps-down` 只停止三应用，不影响 `/opt/infra`。

共享 MySQL 必须为每个项目分配独立 database/user；ForgeAI 不得与其他项目共用 schema 或账号。
共享 Redis 与 Qdrant 分别使用 `FORGE_REDIS_NAMESPACE` 和
`FORGE_QDRANT_COLLECTION_PREFIX` 隔离键和 collection。不同项目不得复用这些前缀。

## 三服务独立镜像与部署

Compose 中三个应用分别引用以下镜像，镜像 tag 由 `FORGE_IMAGE_TAG` 控制：

- `ghcr.io/yanghheda/forge-web`
- `ghcr.io/yanghheda/forge-server`
- `ghcr.io/yanghheda/forge-agent`

`.github/workflows/deploy-services.yml` 在 `main` 分支 push 时按路径选择构建与部署 job：

| 变更路径 | 重建与重启 |
|---|---|
| `forge-web/**` | 仅 `forge-web` |
| `forge-server/**` | 仅 `forge-server` |
| `forge-agent/**` | 仅 `forge-agent` |
| `packages/forge-contracts/**` | `forge-server` 与 `forge-agent` |

Agent 镜像内置 `packages/forge-contracts`，生产环境不再依赖仓库 bind mount。每个 job 推送
commit SHA 与 `latest` 两个 tag，再在服务器执行 `docker compose pull <service>` 和
`docker compose up -d --no-deps --wait <service>`；未命中的服务不会重建或重启。

首次启用前需把本版 `deploy/compose.yml` 同步到服务器的 `/opt/forge-ai`，并在 GitHub
`production` Environment 配置以下 Secrets：

- `DEPLOY_HOST`：服务器地址。
- `DEPLOY_USER`：具有目标目录和 Docker 权限的部署用户。
- `DEPLOY_SSH_PRIVATE_KEY`：专用最小权限 SSH 私钥。
- `DEPLOY_KNOWN_HOSTS`：预先核验的服务器 host key，禁止运行时无条件 `ssh-keyscan`。

可选 Environment Variable `DEPLOY_PATH` 默认为 `/opt/forge-ai`。GitHub Actions 使用短期
`GITHUB_TOKEN` 登录 GHCR；仓库必须允许 Actions 写入 Packages。工作流也可手动选择单个服务或全部服务。

## 测试环境

```bash
make test-apps-up
make test-apps-ready
```

测试结束后使用 `make test-apps-down`。该命令只停止测试应用容器。测试环境 Server 使用
`test` profile 和确定性 demo GitLab provider，但仍会访问 `infra-net`；应使用专门的测试 database/user
和独立 Redis/Qdrant 前缀，禁止指向生产命名空间。

`make smoke` 使用未提交的 `deploy/.env` 在 `127.0.0.1:13000` 临时启动三应用，验证
Web → Server → Agent 调用及无效 Agent 凭据拒绝，完成后只停止应用容器。

不要把 `deploy/.env` 提交到 Git。ForgeAI 仓库内的任何 Compose 命令都不得以 `/opt/infra`
为目标；共享基础设施的备份、恢复和升级必须在服务器级运维流程中完成。

## 独立 GitLab CE

本机真实 GitLab 集成测试使用独立的 `compose.gitlab.yml`，不加入 ForgeAI 主 Compose project，
因此可以按需单独启动和停止：

```bash
make gitlab-up
make gitlab-ready
```

GitLab Web 默认地址为 `http://localhost:8929`，SSH 默认端口为 `2224`。首次启动通常较慢；
初始 `root` 密码可通过以下命令读取：

```bash
docker compose --env-file deploy/.env -f deploy/compose.gitlab.yml exec gitlab \
  grep 'Password:' /etc/gitlab/initial_root_password
```

项目 Pipeline 需要独立 Runner。先在 GitLab 项目的 `Settings → CI/CD → Runners` 创建
Project Runner，启用 `Run untagged jobs`，然后把新生成的认证 Token 只写入未提交的
`deploy/.env`：

```text
FORGE_GITLAB_RUNNER_TOKEN=<新生成的 glrt- Token>
```

注册并启动 Runner：

```bash
make gitlab-runner-up
make gitlab-runner-logs
```

Runner 使用独立配置卷保存注册结果，并通过 GitLab Compose 网络克隆仓库。Token 泄露后应先在
GitLab 删除旧 Runner、创建新 Runner，再更新 `deploy/.env`；不得把真实 Token 写入示例文件或提交。

端口冲突时可在 `deploy/.env` 中修改 `FORGE_GITLAB_HTTP_HOST_PORT` 和
`FORGE_GITLAB_SSH_HOST_PORT`。`make gitlab-down` 会保留 GitLab 的配置、日志和数据卷；
只有显式执行带 `--volumes` 的 Compose down 才会删除这些数据。

Compose Smoke 显式使用 `test` profile，服务地址来自 Compose 环境变量，不会加载个人的
`application-local.yml`。生产 Compose 默认使用 `prod` profile。
