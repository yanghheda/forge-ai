#!/bin/sh
set -eu

compose_file='deploy/compose.yml'
environment_file='deploy/.env.example'

docker compose --env-file "$environment_file" -f "$compose_file" config --quiet

services=$(docker compose --env-file "$environment_file" -f "$compose_file" config --services)
for service in mysql redis qdrant; do
  if ! printf '%s\n' "$services" | grep -qx "$service"; then
    echo "基础设施 Compose 检查失败：缺少 $service 服务" >&2
    exit 1
  fi
done

configuration=$(docker compose --env-file "$environment_file" -f "$compose_file" config --format json)
CONFIGURATION="$configuration" python3 -c '
import json
import os

config = json.loads(os.environ["CONFIGURATION"])
services = config["services"]
for name in ("mysql", "redis", "qdrant"):
    service = services[name]
    assert not service.get("ports"), f"{name} 不得映射宿主机端口"
    assert service.get("healthcheck"), f"{name} 必须声明 healthcheck"
for name in ("mysql", "qdrant"):
    assert services[name].get("volumes"), f"{name} 必须挂载命名卷"
'

echo '基础设施 Compose 契约检查通过'
