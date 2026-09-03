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

mkdir -p "$test_directory/generated"
printf 'ignored.md\ngenerated/*\n' > "$test_directory/.formatignore"
printf '允许忽略的历史文件' > "$test_directory/ignored.md"
printf '允许忽略的生成文件 \n' > "$test_directory/generated/output.md"
./scripts/check-format.sh "$test_directory"

printf '仍须拒绝的文件' > "$test_directory/not-ignored.md"
if ./scripts/check-format.sh "$test_directory" >/dev/null 2>&1; then
  echo '基线测试失败：格式忽略清单错误跳过了未匹配文件' >&2
  exit 1
fi

echo '仓库基线测试通过，已验证格式门禁及忽略清单的成功与失败路径'
