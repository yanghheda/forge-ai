#!/bin/sh
set -eu

# forge-agent 仍处于会话 05，本轮只确认其正式边界说明存在。
test -s "forge-agent/README.md"

./forge-server/mvnw --no-transfer-progress -f forge-server/pom.xml -DskipTests package

test -s forge-server/target/forge-server-0.1.0-SNAPSHOT.jar

cd forge-web
npm run build

echo 'forge-server 与 forge-web 构建通过；forge-agent 将在会话 05 启用'
