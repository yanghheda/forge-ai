#!/bin/sh
set -eu

cd forge-web
npm run typecheck
npm test

echo 'forge-web 类型检查与单元测试通过'
