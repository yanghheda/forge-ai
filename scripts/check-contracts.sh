#!/bin/sh
set -eu

agent_python=${FORGE_AGENT_PYTHON:-python3}
export PYTHONPATH='forge-agent/src'

"$agent_python" -m forge_agent.contracts.validator packages/forge-contracts

