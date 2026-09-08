#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${FORGE_DEMO_ENV_FILE:-${repository_root}/deploy/.env}"
compose_file="${repository_root}/deploy/compose.yml"
fixture_file="${repository_root}/deploy/demo/golden-demo.sql"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少 ${env_file}；请先从 deploy/.env.example 创建本机配置。" >&2
  exit 1
fi

docker compose --env-file "${env_file}" -f "${compose_file}" exec -T mysql \
  sh -c 'exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < "${fixture_file}"

echo "黄金 Demo 已装载：workspace=demo project=DEMO requirement=DEMO-1"
echo "登录：owner@demo.forgeai.local / ForgeAI-Demo-2026!"
