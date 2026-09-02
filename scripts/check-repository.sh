#!/bin/sh
set -eu

required_files='README.md AGENTS.md CONTRIBUTING.md .editorconfig .gitignore Makefile'
required_directories='forge-web forge-server forge-agent packages deploy docs/adr scripts tests .github/workflows'

for file in $required_files; do
  if [ ! -s "$file" ]; then
    echo "仓库检查失败：缺少必需文件 $file" >&2
    exit 1
  fi
done

for directory in $required_directories; do
  if [ ! -d "$directory" ]; then
    echo "仓库检查失败：缺少必需目录 $directory" >&2
    exit 1
  fi
done

for module in forge-web forge-server forge-agent packages deploy; do
  if [ ! -s "$module/README.md" ]; then
    echo "仓库检查失败：$module 缺少边界说明" >&2
    exit 1
  fi
done

for legacy_directory in apps backend agent-service; do
  if [ -d "$legacy_directory" ]; then
    echo "仓库检查失败：发现旧目录名 $legacy_directory" >&2
    exit 1
  fi
done

for id in 001 002 010 011 013; do
  adr=$(find docs/adr -maxdepth 1 -name "ADR-${id}-*.md" -print)
  if [ -z "$adr" ] || [ "$(printf '%s\n' "$adr" | wc -l | tr -d ' ')" -ne 1 ]; then
    echo "仓库检查失败：ADR-${id} 必须存在且只能有一份" >&2
    exit 1
  fi

  for heading in '## 上下文' '## 决策' '## 后果' '## 替代方案'; do
    if ! grep -qx "$heading" "$adr"; then
      echo "仓库检查失败：$adr 缺少章节 $heading" >&2
      exit 1
    fi
  done

  if ! grep -qx -- '- 状态：Accepted' "$adr"; then
    echo "仓库检查失败：$adr 缺少 Accepted 状态" >&2
    exit 1
  fi
done

potential_secret_files=$(git ls-files --cached --others --exclude-standard \
  | grep -E '(^|/)\.env(\.local)?$|\.(pem|key)$' || true)
if [ -n "$potential_secret_files" ]; then
  printf '%s\n' "$potential_secret_files" >&2
  echo '仓库检查失败：发现可能包含 Secret 的文件' >&2
  exit 1
fi

if ! grep -q 'Java 生产代码' AGENTS.md || ! grep -q '中文普通块注释' AGENTS.md; then
  echo '仓库检查失败：AGENTS.md 缺少 Java 成员中文普通块注释规则' >&2
  exit 1
fi

echo '仓库结构与治理规则检查通过'
