#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${FORGE_DEMO_ENV_FILE:-${repository_root}/deploy/.env}"
compose_file="${repository_root}/deploy/compose.yml"
test_compose_file="${repository_root}/deploy/compose-test.yml"
api_base_url="${FORGE_PERF_API_BASE_URL:-http://localhost:3000/api}"
request_origin="${FORGE_PERF_ORIGIN:-${api_base_url%/api}}"
samples="${FORGE_PERF_SAMPLES:-100}"
budget_seconds="${FORGE_PERF_P95_BUDGET_SECONDS:-0.300}"
fixture_file="${repository_root}/deploy/demo/session33-performance.sql"
cookie_file="$(mktemp)"
timing_file="$(mktemp)"
response_file="$(mktemp)"
trap 'rm -f "${cookie_file}" "${timing_file}" "${response_file}"' EXIT

if [[ ! -f "${env_file}" ]]; then
  echo "缺少 ${env_file}；请先创建本机 Compose 配置。" >&2
  exit 1
fi

"${repository_root}/scripts/load-golden-demo.sh" >/dev/null
docker compose --env-file "${env_file}" -f "${compose_file}" -f "${test_compose_file}" exec -T mysql \
  sh -c 'exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < "${fixture_file}"

explain_sql="EXPLAIN SELECT id,item_key,type,title,status,priority,assignee_user_id,due_at,version,created_at,updated_at FROM work_items WHERE workspace_id=32004 AND project_id=33007 AND type='DEV_TASK' AND status='TODO' AND deleted_at IS NULL ORDER BY item_number DESC LIMIT 100 OFFSET 0"
plan="$(docker compose --env-file "${env_file}" -f "${compose_file}" -f "${test_compose_file}" exec -T mysql \
  sh -c 'exec mysql --batch --skip-column-names -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "$1"' \
  shell "${explain_sql}")"
if [[ "${plan}" != *"idx_work_items_scope_type_status_page"* ]]; then
  echo "查询计划未选择会话 33 分页索引：${plan}" >&2
  exit 1
fi

csrf_response="$(curl --fail --silent --show-error \
  --cookie-jar "${cookie_file}" \
  "${api_base_url}/v1/auth/csrf")"
csrf_token="$(printf '%s' "${csrf_response}" | jq -er '.data.token')"
curl --fail --silent --show-error \
  --cookie "${cookie_file}" \
  --cookie-jar "${cookie_file}" \
  --header 'Content-Type: application/json' \
  --header "Origin: ${request_origin}" \
  --header "X-CSRF-TOKEN: ${csrf_token}" \
  --data '{"email":"owner@demo.forgeai.local","password":"ForgeAI-Demo-2026!"}' \
  "${api_base_url}/v1/auth/login" \
  --output /dev/null

for ((sample = 1; sample <= samples; sample++)); do
  result="$(curl --silent --show-error \
    --cookie "${cookie_file}" \
    --output "${response_file}" \
    --write-out '%{http_code} %{time_total}' \
    "${api_base_url}/v1/work-items?workspaceId=32004&projectId=33007&type=DEV_TASK&status=TODO&page=1&pageSize=100")"
  status="${result%% *}"
  elapsed="${result##* }"
  if [[ "${status}" != "200" ]]; then
    echo "分页请求失败：HTTP ${status}" >&2
    exit 1
  fi
  if ! jq -e '(.data.items | type == "array") and ((.data.items | length) > 0)' \
    "${response_file}" >/dev/null; then
    echo '分页响应缺少非空 items 数组' >&2
    exit 1
  fi
  if jq -e 'any(.data.items[]; has("description"))' "${response_file}" >/dev/null; then
    echo '分页响应意外包含 description 正文' >&2
    exit 1
  else
    jq_status="$?"
    if [[ "${jq_status}" -ne 1 ]]; then
      echo '分页响应正文检查失败' >&2
      exit "${jq_status}"
    fi
  fi
  printf '%s\n' "${elapsed}" >> "${timing_file}"
done

p95="$(LC_ALL=C sort -n "${timing_file}" | awk -v count="${samples}" 'NR == int((count * 95 + 99) / 100) { print; exit }')"
awk -v p95="${p95}" -v budget="${budget_seconds}" 'BEGIN { exit !(p95 <= budget) }' || {
  echo "Work Item 列表 P95 ${p95}s 超过预算 ${budget_seconds}s" >&2
  exit 1
}

echo "Work Item 列表：samples=${samples} P95=${p95}s budget=${budget_seconds}s"
echo 'EXPLAIN：idx_work_items_scope_type_status_page'
