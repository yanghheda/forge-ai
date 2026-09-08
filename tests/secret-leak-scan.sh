#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_root="$(mktemp -d)"
trap 'rm -rf "${fixture_root}"' EXIT

safe_file="${fixture_root}/safe.log"
unsafe_file="${fixture_root}/unsafe.log"
printf '%s\n' 'authorization=[REDACTED]' > "${safe_file}"
printf '%s\n' 'token=glpat-1234567890abcdefghijklmn' > "${unsafe_file}"

"${repository_root}/scripts/check-secret-leaks.sh" "${safe_file}"
if "${repository_root}/scripts/check-secret-leaks.sh" "${unsafe_file}" >/dev/null 2>&1; then
  echo 'Secret 扫描失败路径未阻断' >&2
  exit 1
fi

# 最小化 PATH，验证未安装 ripgrep 的普通终端仍可安全扫描。
PATH=/usr/bin:/bin "${repository_root}/scripts/check-secret-leaks.sh" "${safe_file}"
if PATH=/usr/bin:/bin "${repository_root}/scripts/check-secret-leaks.sh" "${unsafe_file}" >/dev/null 2>&1; then
  echo '无 ripgrep 时 Secret 扫描失败路径未阻断' >&2
  exit 1
fi

echo 'Secret 扫描正向与负向测试通过'
