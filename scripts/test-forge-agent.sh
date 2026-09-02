#!/bin/sh
set -eu

agent_python=${FORGE_AGENT_PYTHON:-python3}
export PYTHONPATH='forge-agent/src'

"$agent_python" -m ruff check forge-agent/src forge-agent/tests
"$agent_python" -m ruff format --check forge-agent/src forge-agent/tests
"$agent_python" -m pytest -q forge-agent/tests

echo 'forge-agent lint、格式与单元测试通过'

