# ADR-027：生产环境经共享边缘 nginx 以子域名接入 ForgeAI

- 状态：Accepted
- 日期：2026-10-13
- 修订：ADR-020 中「只有 `forge-server` 加入 `infra-net`」这一条网络接入范围

## 上下文

ADR-020 决定单机共享基础设施：MySQL、Redis、Qdrant 与**容器化边缘 nginx**由项目外的
`/opt/infra` 统一部署，业务项目只以 `external` 方式加入共享网络 `infra-net`。该 nginx 是
唯一占用宿主机 80/443 的服务，且本身只挂在 `infra-net` 上。

ForgeAI 需要公网唯一 HTTPS 入口，而当前 `deploy/compose.yml` 中 `forge-web` 只把端口发布到
宿主机回环 `127.0.0.1:3000`，且只加入项目私有的 `forge-edge` / `forge-internal` 网络。容器化
nginx 无法访问宿主机回环端口（容器内的 `127.0.0.1` 是容器自身），也访问不到未与它共享任何网络的
其它容器，因此该形态下共享边缘 nginx **无法**把请求转发到 `forge-web`。

同时被否掉的两种接法各有代价：以子路径 `/forge-ai/` 挂到 `yjxai.cloud` 下，会迫使前端构建
注入 `base` 与 API 前缀（Vite `base` + `VITE_API_BASE_URL`），并让同源 `/api` 与同域其它项目
争抢前缀；把 `forge-web` 改为发布 `0.0.0.0:3000` 则违反共享服务器「业务容器不得映射宿主机
端口」的约定，并把端口暴露到宿主机的全部网卡。

## 决策

- 生产入口使用**独立子域名** `forgeai.yjxai.cloud`：前端所有资源与 `/api` 都落在子域名根路径，
  `forge-web` 的静态资源解析与同源 API 代理契约保持不变，前端与构建无需任何改动。
- 生产 Compose 中 **`forge-web` 加入共享网络 `infra-net` 并声明别名 `forge-web`**；共享边缘
  nginx 按子域名分流：静态资源与 SPA 交给 `forge-web:3000`，`/api/` 直连 `forge-server:8080`。
  同时**移除 `forge-web` 的宿主机端口发布**，并删除仅由它使用的 `forge-edge` 网络。
- 边缘配置使用 `resolve` 形式的 upstream（本项目容器缺席时只有本域名 502，不影响同机其它
  站点），并对 Agent 运行事件与站内通知两条 SSE 接口显式关闭 `proxy_buffering`、保留
  `proxy_http_version 1.1`。该配置以同源副本形式保存在 `deploy/nginx/forgeai.yjxai.cloud.conf`，
  服务器上部署到 `/opt/infra/nginx/conf.d/`。
- `forge-web` 进入 `infra-net` 的**唯一目的是让边缘 nginx 可达其 3000 端口**：它不接收任何
  MySQL / Redis / Qdrant 凭据，也不得在代码中直连这些服务；业务数据能力仍然只能经
  `forge-server` 获取（ADR-002、ADR-020 的数据职责与命名空间隔离结论不变）。
- 生产 `forge-server` 的 `FORGE_ALLOWED_ORIGINS` 收敛为该子域名（`https://forgeai.yjxai.cloud`），
  不保留 `localhost` 等开发来源。

## 后果

- 生产不再需要宿主机端口，端口占用完全交给共享边缘 nginx；本地与 Compose Smoke 仍由
  `compose-test.yml` 覆盖发布到 `127.0.0.1` 回环端口，开发体验不变。
- 引入子域名带来的运维前置项：DNS A 记录、该子域名的独立证书签发与续期，均属服务器级
  运维动作，必须在首次部署时完成。
- 共享网络的信任假设由「仅 `forge-server`」扩大为「`forge-web` 与 `forge-server` 均为边缘
  可达」。隔离边界因此更加依赖凭据隔离与代码纪律：`forge-web` 一旦混入数据服务凭据，即
  等于把数据面暴露给边缘网络。
- 边缘 nginx 配置成为 ForgeAI 上线的外部依赖；改错会导致同机所有站点不可用，必须先
  `nginx -t` 再 reload。

## 替代方案

- 子路径 `/forge-ai/` 接入同域：需要前端 `base` 与 API 前缀改造，且与同域其它项目的
  `/api`、`/assets` 前缀冲突，不采用。
- 由 nginx 直接托管 `forge-web` 构建产物（rsync 到 `/var/www`）并把 `/api` 反代 `forge-server`：
  可行且不触碰 ADR-020，但放弃了三容器独立镜像与按路径部署的既有 CI 契约，需要重写部署
  工作流与相关测试，本次不采用。
- `forge-web` 发布 `0.0.0.0:3000` 供边缘 nginx 经宿主机网关访问：违反共享服务器「业务容器
  不得映射宿主机端口」约定，且把端口暴露到全部网卡，不采用。
- 把 `forge-web` 与 `infra-net` 之外的网络打通（运行时 `docker network connect` 到 `infra-nginx`）：
  该连接会在基础设施容器重建后丢失，属于不可复现的临时状态，不采用。
