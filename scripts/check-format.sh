#!/bin/sh
set -eu

# 可传入独立目录，以便测试门禁的失败路径而不污染工作树。
check_root=${1:-.}
failed=0

find "$check_root" -type f \
  ! -path '*/.git/*' \
  ! -path '*/node_modules/*' \
  ! -path '*/.next/*' \
  ! -path '*/build/*' \
  ! -path '*/target/*' \
  \( -name '*.md' -o -name '*.yml' -o -name '*.yaml' -o -name '*.json' \
     -o -name '*.java' -o -name '*.py' -o -name '*.sh' -o -name 'Makefile' \
     -o -name '.editorconfig' -o -name '.gitignore' \) \
  -print | LC_ALL=C sort | while IFS= read -r file; do
    case "$file" in
      *.md)
        # Markdown 行尾双空格表示显式换行；其他尾随空白仍应阻断。
        whitespace_errors=$(LC_ALL=C grep -n '[[:blank:]]$' "$file" | grep -v '  $' || true)
        ;;
      *)
        whitespace_errors=$(LC_ALL=C grep -n '[[:blank:]]$' "$file" || true)
        ;;
    esac

    if [ -n "$whitespace_errors" ]; then
      printf '%s\n' "$whitespace_errors"
      echo "格式错误：$file 包含行尾空白" >&2
      failed=1
    fi

    if [ -s "$file" ] && [ "$(tail -c 1 "$file" | wc -l | tr -d ' ')" -eq 0 ]; then
      echo "格式错误：$file 缺少文件末尾换行" >&2
      failed=1
    fi

    if [ "$failed" -ne 0 ]; then
      exit 1
    fi
  done
