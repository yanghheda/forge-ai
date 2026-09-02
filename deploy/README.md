# deploy

自托管部署资产目录。部署配置必须通过环境变量或 Secret 文件注入凭据，不得提交真实 Secret。

## 本机基础设施

本轮 Compose 只包含 MySQL、Redis 和 Qdrant；三应用将在后续 P0 会话接入。首次启动前复制示例配置并更换本机密码：

```bash
cp deploy/.env.example deploy/.env
make infra-up
make infra-ready
```

三项服务只加入 `forge-internal` Compose 网络，不映射宿主机端口。MySQL 与 Qdrant 使用命名卷；Redis 明确作为可丢失的短期状态运行。`make infra-down` 会保留数据卷。

不要把 `deploy/.env` 提交到 Git。确需从宿主机 IDE 调试数据服务时，可自行创建不提交的 Compose override 文件。`docker compose down -v` 会删除 MySQL/Qdrant 本机数据，只能在明确重置环境时手工执行。
