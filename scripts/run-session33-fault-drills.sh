#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${FORGE_DEMO_ENV_FILE:-${repository_root}/deploy/.env}"
compose_file="${repository_root}/deploy/compose.yml"
api_base_url="${FORGE_FAULT_API_BASE_URL:-http://localhost:3000/api}"

compose() {
  docker compose --profile applications --env-file "${env_file}" -f "${compose_file}" "$@"
}

recover() {
  compose up -d --wait mysql redis qdrant forge-agent forge-server forge-web >/dev/null
}

expect_server_health() {
  local expected="$1"
  local body
  body="$(compose exec -T forge-server wget -qO- http://127.0.0.1:8080/actuator/health || true)"
  if [[ "${body}" != *"\"status\":\"${expected}\""* ]]; then
    echo "期望 Server 健康状态 ${expected}，实际响应：${body}" >&2
    return 1
  fi
}

trap recover EXIT
recover

compose stop qdrant >/dev/null
expect_server_health DEGRADED
curl --fail --silent "${api_base_url}/v1/system/status" >/dev/null
compose up -d --wait qdrant >/dev/null

compose stop forge-agent >/dev/null
expect_server_health DEGRADED
curl --fail --silent "${api_base_url}/v1/system/status" >/dev/null
compose up -d --wait forge-agent >/dev/null

compose stop redis >/dev/null
if compose exec -T forge-server wget -q --spider http://127.0.0.1:8080/actuator/health/readiness; then
  echo 'Redis 故障时 readiness 意外放行' >&2
  exit 1
fi
compose up -d --wait redis >/dev/null

compose stop mysql >/dev/null
if compose exec -T forge-server wget -q --spider http://127.0.0.1:8080/actuator/health/readiness; then
  echo 'MySQL 故障时 readiness 意外放行' >&2
  exit 1
fi
compose up -d --wait mysql >/dev/null

echo 'DB/Redis 均 fail closed；Qdrant/Agent 故障时人工主流程入口保持可用'
