#!/bin/sh
set -eu

compose_file='deploy/compose.yml'
test_compose_file='deploy/compose-test.yml'
gitlab_compose_file='deploy/compose.gitlab.yml'
environment_file='deploy/.env.example'

docker compose --profile applications --env-file "$environment_file" -f "$compose_file" config --quiet
docker compose --profile applications --env-file "$environment_file" -f "$compose_file" -f "$test_compose_file" config --quiet
docker compose --env-file "$environment_file" -f "$gitlab_compose_file" config --quiet

for variable in FORGE_MYSQL_URL FORGE_MYSQL_USERNAME \
  FORGE_REDIS_HOST FORGE_REDIS_PORT FORGE_QDRANT_BASE_URL FORGE_AGENT_BASE_URL; do
  if ! grep -q "\${${variable}:" forge-server/src/main/resources/application-dev.yml; then
    echo "基础设施配置检查失败：application-dev.yml 必须允许 ${variable} 覆盖本机默认值" >&2
    exit 1
  fi
done
if ! grep -q '^    allowed-origins: ${FORGE_ALLOWED_ORIGINS:http://localhost}$' \
  forge-server/src/main/resources/application-test.yml; then
  echo '基础设施配置检查失败：test profile 必须允许 Compose 注入 Web Origin 白名单' >&2
  exit 1
fi
if ! grep -q '^    password: ${FORGE_MYSQL_PASSWORD}$' forge-server/src/main/resources/application-dev.yml; then
  echo '基础设施配置检查失败：application-dev.yml 不得提交数据库密码默认值' >&2
  exit 1
fi

services=$(docker compose --profile applications --env-file "$environment_file" -f "$compose_file" config --services)
for service in forge-web forge-server forge-agent; do
  if ! printf '%s\n' "$services" | grep -qx "$service"; then
    echo "应用 Compose 检查失败：缺少 $service 服务" >&2
    exit 1
  fi
done
for service in mysql redis qdrant; do
  if printf '%s\n' "$services" | grep -qx "$service"; then
    echo "应用 Compose 检查失败：不得包含 $service 服务" >&2
    exit 1
  fi
done

configuration=$(docker compose --profile applications --env-file "$environment_file" -f "$compose_file" config --format json)
CONFIGURATION="$configuration" python3 -c '
import json
import os

config = json.loads(os.environ["CONFIGURATION"])
services = config["services"]
assert set(services) == {"forge-web", "forge-server", "forge-agent"}
for name in services:
    assert services[name].get("healthcheck"), f"{name} 必须声明 healthcheck"
assert services["forge-server"]["environment"]["SPRING_PROFILES_ACTIVE"] == "prod"
assert services["forge-server"]["environment"]["FORGE_GITLAB_PROVIDER"] == "real"
server_build = services["forge-server"].get("build", {})
assert server_build.get("context", "").endswith("/forge-ai")
assert server_build.get("dockerfile", "").endswith("forge-server/Dockerfile")
for name in ("forge-server", "forge-agent"):
    assert not services[name].get("ports"), f"{name} 不得映射宿主机端口"
web_ports = services["forge-web"].get("ports", [])
assert len(web_ports) == 1 and web_ports[0].get("host_ip") == "127.0.0.1"
assert set(services["forge-web"].get("networks", {})) == {"forge-edge", "forge-internal"}
assert set(services["forge-server"].get("networks", {})) == {"forge-internal", "shared-infrastructure"}
assert set(services["forge-agent"].get("networks", {})) == {"forge-internal"}
assert config["networks"]["forge-internal"].get("internal") is True
shared = config["networks"]["shared-infrastructure"]
assert shared.get("external") is True, "infra-net 必须由 /opt/infra 在项目外创建"
assert shared.get("name") == "infra-net"
server_environment = services["forge-server"].get("environment", {})
assert server_environment.get("FORGE_REDIS_NAMESPACE") == "forge-ai"
assert server_environment.get("FORGE_QDRANT_COLLECTION_PREFIX") == "forge_ai_"
agent_environment = services["forge-agent"].get("environment", {})
for forbidden in ("FORGE_MYSQL_URL", "FORGE_MYSQL_PASSWORD", "GITLAB_TOKEN", "DEPLOY_TOKEN"):
    assert forbidden not in agent_environment, f"forge-agent 不得接收 {forbidden}"
'

echo '外部共享基础设施拓扑契约检查通过'

test_configuration=$(docker compose --profile applications --env-file "$environment_file" -f "$compose_file" -f "$test_compose_file" config --format json)
TEST_CONFIGURATION="$test_configuration" python3 -c '
import json
import os

config = json.loads(os.environ["TEST_CONFIGURATION"])
services = config["services"]
assert config.get("name") == "forge-ai-test"
assert set(services) == {"forge-web", "forge-server", "forge-agent"}
assert services["forge-server"]["environment"]["SPRING_PROFILES_ACTIVE"] == "test"
assert services["forge-server"]["environment"]["FORGE_GITLAB_PROVIDER"] == "demo"
assert config["networks"]["shared-infrastructure"].get("external") is True
web_ports = services["forge-web"].get("ports", [])
assert len(web_ports) == 1 and web_ports[0].get("published") == "13000"
'

echo '测试应用 Compose 契约检查通过'

gitlab_configuration=$(docker compose --env-file "$environment_file" -f "$gitlab_compose_file" config --format json)
GITLAB_CONFIGURATION="$gitlab_configuration" python3 -c '
import json
import os

config = json.loads(os.environ["GITLAB_CONFIGURATION"])
assert config.get("name") == "forge-gitlab"
assert set(config["services"]) == {"gitlab"}
service = config["services"]["gitlab"]
assert service.get("healthcheck")
assert service.get("restart") == "unless-stopped"
assert len(service.get("volumes", [])) == 3
ports = service.get("ports", [])
assert {port.get("target") for port in ports} == {22, 8929}
assert all(port.get("host_ip") == "127.0.0.1" for port in ports)
'

echo '独立 GitLab Compose 契约检查通过'
