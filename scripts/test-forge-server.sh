#!/bin/sh
set -eu

# 统一从仓库根目录执行，确保源码质量门禁扫描的是正式生产目录。
./forge-server/mvnw --no-transfer-progress -f forge-server/pom.xml test
