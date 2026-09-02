#!/bin/sh
set -eu

compose_file='deploy/compose.yml'
host_dev_compose_file='deploy/compose.host-dev.yml.example'
environment_file='deploy/.env.example'

docker compose --profile applications --env-file "$environment_file" -f "$compose_file" config --quiet
docker compose --env-file "$environment_file" -f "$compose_file" -f "$host_dev_compose_file" config --quiet

for variable in FORGE_MYSQL_URL FORGE_MYSQL_USERNAME \
  FORGE_REDIS_HOST FORGE_REDIS_PORT FORGE_QDRANT_BASE_URL FORGE_AGENT_BASE_URL; do
  if ! grep -q "\${${variable}:" forge-server/src/main/resources/application-dev.yml; then
    echo "基础设施配置检查失败：application-dev.yml 必须允许 ${variable} 覆盖本机默认值" >&2
    exit 1
  fi
done
if ! grep -q '^    password: ${FORGE_MYSQL_PASSWORD}$' forge-server/src/main/resources/application-dev.yml; then
  echo '基础设施配置检查失败：application-dev.yml 不得提交数据库密码默认值' >&2
  exit 1
fi

services=$(docker compose --profile applications --env-file "$environment_file" -f "$compose_file" config --services)
for service in forge-web forge-server forge-agent mysql redis qdrant; do
  if ! printf '%s\n' "$services" | grep -qx "$service"; then
    echo "基础设施 Compose 检查失败：缺少 $service 服务" >&2
    exit 1
  fi
done

configuration=$(docker compose --profile applications --env-file "$environment_file" -f "$compose_file" config --format json)
CONFIGURATION="$configuration" python3 -c '
import json
import os

config = json.loads(os.environ["CONFIGURATION"])
services = config["services"]
for name in ("forge-web", "forge-server", "forge-agent", "mysql", "redis", "qdrant"):
    service = services[name]
    assert service.get("healthcheck"), f"{name} 必须声明 healthcheck"
assert services["forge-server"].get("environment", {}).get("SPRING_PROFILES_ACTIVE") == "test", (
    "Compose Smoke 中 forge-server 必须显式使用 test profile"
)
for name in ("forge-server", "forge-agent", "mysql", "redis", "qdrant"):
    assert not services[name].get("ports"), f"{name} 不得映射宿主机端口"
web_ports = services["forge-web"].get("ports", [])
assert len(web_ports) == 1, "forge-web 必须是唯一宿主机入口"
assert web_ports[0].get("host_ip") == "127.0.0.1", "forge-web 只能绑定本机回环地址"
assert services["forge-web"].get("environment", {}).get("HOSTNAME") == "0.0.0.0", (
    "forge-web 必须监听容器全部接口"
)
assert set(services["forge-web"].get("networks", {})) == {"forge-edge", "forge-internal"}, (
    "forge-web 必须同时接入入口网络与内部网络"
)
assert config["networks"]["forge-internal"].get("internal") is True, "内部网络必须隔离外部访问"
assert config["networks"]["forge-edge"].get("internal") is not True, "入口网络必须允许回环端口转发"
for name in ("mysql", "qdrant"):
    assert services[name].get("volumes"), f"{name} 必须挂载命名卷"
agent_environment = services["forge-agent"].get("environment", {})
for forbidden in ("FORGE_MYSQL_URL", "FORGE_MYSQL_PASSWORD", "GITLAB_TOKEN", "DEPLOY_TOKEN"):
    assert forbidden not in agent_environment, f"forge-agent 不得接收 {forbidden}"
'

echo '基础设施 Compose 契约检查通过'

host_dev_configuration=$(docker compose --env-file "$environment_file" -f "$compose_file" -f "$host_dev_compose_file" config --format json)
HOST_DEV_CONFIGURATION="$host_dev_configuration" python3 -c '
import json
import os

config = json.loads(os.environ["HOST_DEV_CONFIGURATION"])
services = config["services"]
assert config["networks"]["forge-internal"].get("internal") is not True, (
    "宿主机开发覆盖必须允许回环端口转发"
)
expected_ports = {"mysql": 3306, "redis": 6379, "qdrant": 6333}
for name, target in expected_ports.items():
    ports = services[name].get("ports", [])
    assert len(ports) == 1, f"{name} 本机开发覆盖必须且只能映射一个端口"
    assert ports[0].get("host_ip") == "127.0.0.1", f"{name} 只能绑定本机回环地址"
    assert ports[0].get("target") == target, f"{name} 容器端口必须是 {target}"
'

echo '宿主机开发 Compose 契约检查通过'
