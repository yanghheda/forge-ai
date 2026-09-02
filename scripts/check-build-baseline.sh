#!/bin/sh
set -eu

agent_python=${FORGE_AGENT_PYTHON:-python3}

./forge-server/mvnw --no-transfer-progress -f forge-server/pom.xml -DskipTests package

test -s forge-server/target/forge-server-0.1.0-SNAPSHOT.jar

cd forge-web
npm run build
cd ..

PYTHONPATH=forge-agent/src "$agent_python" -m compileall -q forge-agent/src

echo 'forge-server、forge-web 与 forge-agent 构建检查通过'
