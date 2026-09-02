#!/bin/sh
set -eu

./scripts/check-repository.sh

test_directory=$(mktemp -d "${TMPDIR:-/tmp}/forge-ai-format-test.XXXXXX")
trap 'rm -rf "$test_directory"' EXIT HUP INT TERM

printf '存在尾随空格 \n' > "$test_directory/invalid.md"
if ./scripts/check-format.sh "$test_directory" >/dev/null 2>&1; then
  echo '基线测试失败：格式门禁没有拒绝尾随空格' >&2
  exit 1
fi

printf '格式正确\n' > "$test_directory/valid.md"
rm "$test_directory/invalid.md"
./scripts/check-format.sh "$test_directory"

echo '仓库基线测试通过，已验证格式门禁的成功与失败路径'
