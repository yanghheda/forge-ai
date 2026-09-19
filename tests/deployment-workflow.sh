#!/bin/sh
set -eu

workflow='.github/workflows/deploy-services.yml'
compose_file='deploy/compose.yml'
agent_dockerfile='forge-agent/Dockerfile'

if [ ! -s "$workflow" ]; then
  echo '部署工作流检查失败：缺少 deploy-services.yml' >&2
  exit 1
fi

for service in forge-web forge-server forge-agent; do
  if ! grep -q "docker compose.*up -d --no-deps.*${service}" "$workflow"; then
    echo "部署工作流检查失败：${service} 必须独立更新且不得重启依赖" >&2
    exit 1
  fi
  if ! grep -q "${service}/" "$workflow"; then
    echo "部署工作流检查失败：缺少 ${service} 路径过滤" >&2
    exit 1
  fi
done

contracts_filters=$(grep -c 'packages/forge-contracts/' "$workflow")
if [ "$contracts_filters" -ne 2 ]; then
  echo '部署工作流检查失败：共享契约必须且只能触发 Server 与 Agent' >&2
  exit 1
fi

if [ "$(grep -c '^COPY .*packages/forge-contracts' "$agent_dockerfile")" -ne 2 ]; then
  echo '部署工作流检查失败：Agent 镜像必须内置共享契约' >&2
  exit 1
fi
if grep -q 'packages/forge-contracts:/app/contracts' "$compose_file"; then
  echo '部署工作流检查失败：生产 Agent 不得依赖仓库 bind mount' >&2
  exit 1
fi

for service in forge-web forge-server forge-agent; do
  if ! grep -A8 "^  ${service}:" "$compose_file" | grep -q 'image:.*FORGE_IMAGE_TAG'; then
    echo "部署工作流检查失败：${service} 必须引用可独立打标的镜像" >&2
    exit 1
  fi
done

echo '三服务独立镜像与按路径部署契约检查通过'
