#!/bin/sh
set -eu

compose_file='deploy/compose.yml'
test_compose_file='deploy/compose-test.yml'
environment_file='deploy/.env.example'
smoke_port='13000'
export FORGE_WEB_PORT="$smoke_port"

cleanup() {
  docker compose --profile applications --env-file "$environment_file" -f "$compose_file" -f "$test_compose_file" down
}
trap cleanup EXIT INT TERM

docker compose --profile applications --env-file "$environment_file" -f "$compose_file" -f "$test_compose_file" up -d --build --wait

curl --fail --silent --show-error "http://127.0.0.1:${smoke_port}/healthz" \
  | grep -q '"application":"forge-web"'
curl --fail --silent --show-error "http://127.0.0.1:${smoke_port}/api/v1/system/status" \
  | grep -q '"application":"forge-server"'

docker compose --profile applications --env-file "$environment_file" -f "$compose_file" -f "$test_compose_file" \
  exec -T forge-server wget -qO- http://127.0.0.1:8080/actuator/health/agent \
  | grep -q '"status":"UP"'

docker compose --profile applications --env-file "$environment_file" -f "$compose_file" -f "$test_compose_file" \
  exec -T forge-agent python -c '
import urllib.error
import urllib.request

request = urllib.request.Request(
    "http://127.0.0.1:8000/internal/v1/health/ready",
    headers={"Authorization": "Bearer invalid"},
)
try:
    urllib.request.urlopen(request, timeout=2)
except urllib.error.HTTPError as error:
    if error.code == 401:
        raise SystemExit(0)
raise SystemExit("Agent 内部健康端点未拒绝无效凭据")
'

echo '三应用 Compose Smoke 通过：Web → Server → Agent 正常，Agent 无效凭据被拒绝'
