#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pattern='glpat-[A-Za-z0-9_-]{20,}|sk-[A-Za-z0-9]{20,}|-----BEGIN ([A-Z ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+eyJ[A-Za-z0-9_-]{16,}'

scan_file() {
  local file="$1"
  local matches
  local status
  if matches="$(rg --line-number --pcre2 "${pattern}" "${file}")"; then
    printf '%s\n' "${matches}"
    echo "Secret 扫描失败：${file} 命中高置信凭据格式" >&2
    return 1
  else
    status="$?"
  fi
  if [[ "${status}" -ne 1 ]]; then
    echo "Secret 扫描失败：无法读取 ${file}" >&2
    return "${status}"
  fi
}

if [[ "$#" -gt 0 ]]; then
  for file in "$@"; do
    scan_file "${file}"
  done
else
  while IFS= read -r file; do
    case "${file}" in
      tests/*|*/test/*|*/tests/*|*.example|*.lock)
        continue
        ;;
    esac
    scan_file "${repository_root}/${file}"
  done < <(git -C "${repository_root}" -c core.quotepath=false \
    ls-files --cached --others --exclude-standard)
fi

echo "Secret 高置信格式扫描通过"
