#!/bin/sh
set -eu

# forge-web 与 forge-agent 仍处于后续 P0 会话，本轮只构建已具备运行时的 forge-server。
for pending_module in forge-web forge-agent; do
  test -s "$pending_module/README.md"
done

./forge-server/mvnw --no-transfer-progress -f forge-server/pom.xml -DskipTests package

test -s forge-server/target/forge-server-0.1.0-SNAPSHOT.jar

echo 'forge-server 构建通过；forge-web 与 forge-agent 将在各自 P0 骨架会话启用'
